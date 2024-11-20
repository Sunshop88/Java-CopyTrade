package net.maku.subcontrol.even;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cld.message.pubsub.kafka.IKafkaProducer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.OrderChangeTypeEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.AccountCacheVO;
import net.maku.followcom.vo.FollowTraderCacheVO;
import net.maku.followcom.vo.OrderCacheVO;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import net.maku.subcontrol.util.KafkaTopicUtil;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.OrderUpdateEventArgs;
import online.mtapi.mt4.QuoteClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * mtapi.online 监听MT4账户订单变化
 *
 * @author samson bruce
 */
@Slf4j
public class LeaderOrderUpdateEventHandlerImpl extends OrderUpdateHandler {
    private LeaderApiTradersAdmin leaderApiTradersAdmin = SpringContextUtils.getBean(LeaderApiTradersAdmin.class);
    private RedisUtil redisUtil = SpringContextUtils.getBean(RedisUtil.class);
    private FollowTraderService followTraderService = SpringContextUtils.getBean(FollowTraderService.class);
    private FollowPlatformService followPlatformService = SpringContextUtils.getBean(FollowPlatformService.class);

    // 上次执行时间
    private long lastInvokeTime = 0;

    // 设定时间间隔，单位为毫秒
    private final long interval = 1000; // 1秒间隔

    public LeaderOrderUpdateEventHandlerImpl(AbstractApiTrader abstractApiTrader, IKafkaProducer<String, Object> kafkaProducer) {
        super(kafkaProducer);
        this.abstractApiTrader = abstractApiTrader;
        this.leader = this.abstractApiTrader.getTrader();
        this.topic = KafkaTopicUtil.leaderTradeSignalTopic(leader);
    }

    /**
     * 1-开仓
     * 1.1市场执行 PositionOpen
     * 1.2挂单 PendingOpen->挂单触发 PendingFill
     * <p>
     * 2-修改
     * 持仓修改 PositionModify
     * 挂单修改 PendingModify
     * <p>
     * 3-删除
     * 挂单删除 PendingClose
     * <p>
     * 4-平仓
     * 立即平仓 PositionClose
     * 部分平仓 PositionClose->PositionOpen
     * 止损
     * 止赢
     *
     * @param sender               sender
     * @param orderUpdateEventArgs orderUpdateEventArgs
     */
    @Override
    public void invoke(Object sender, OrderUpdateEventArgs orderUpdateEventArgs) {
        if (!running) {
            return;
        }
        Order order = orderUpdateEventArgs.Order;

        String currency = abstractApiTrader.quoteClient.Account().currency;
        //发送websocket消息标识
        int flag = 0;
        switch (orderUpdateEventArgs.Action) {
            case PositionOpen:
            case PendingFill:
                log.info("[MT4喊单者：{}-{}-{}]监听到" + orderUpdateEventArgs.Action + ",订单信息[{}]", leader.getId(), leader.getAccount(), leader.getServerName(), new EaOrderInfo(order));
                scheduledThreadPoolExecutor.submit(() -> {
                    double equity = 0.0;
                    try {
                        equity = abstractApiTrader.quoteClient.AccountEquity();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    send2Copiers(OrderChangeTypeEnum.NEW, order, equity, currency, LocalDateTime.now());
                });
                flag = 1;
                //推送到redis
                pushCache(leader.getServerId());
                break;
            case PositionClose:
                log.info("[MT4喊单者：{}-{}-{}]监听到" + orderUpdateEventArgs.Action + ",订单信息[{}]", leader.getId(), leader.getAccount(), leader.getServerName(), new EaOrderInfo(order));
                //持仓时间小于2秒，则延迟一秒发送平仓信号，避免客户测试的时候平仓信号先于开仓信号到达
                int delaySendCloseSignal = delaySendCloseSignal(order.OpenTime, order.CloseTime);
                if (delaySendCloseSignal == 0) {
                    scheduledThreadPoolExecutor.submit(() -> {
                        send2Copiers(OrderChangeTypeEnum.CLOSED, order, 0, currency, LocalDateTime.now());
                    });
                } else {
                    scheduledThreadPoolExecutor.schedule(() -> {
                        send2Copiers(OrderChangeTypeEnum.CLOSED, order, 0, currency, LocalDateTime.now());
                    }, delaySendCloseSignal, TimeUnit.MILLISECONDS);
                }
                flag = 1;
                //推送到redis
                pushCache(leader.getServerId());
                break;
            default:
                log.error("Unexpected value: " + orderUpdateEventArgs.Action);
        }
        if (flag == 1) {
            // 判断当前时间与上次执行时间的间隔是否达到设定的间隔时间
            // 获取当前系统时间
            long currentTime = System.currentTimeMillis();
            // 获取该symbol上次执行时间
            if (currentTime - lastInvokeTime >= interval) {
                log.info("推送数据" + new Date());
                lastInvokeTime = currentTime;
                //发送消息
                traderOrderActiveWebSocket.sendPeriodicMessage(leader.getId().toString(), "0");
            }
        }
    }


    /**
     * 推送redis缓存
     */
    private void pushCache(Integer vpsId) {
        ThreadPoolUtils.execute(() -> {
            //查询当前vpsId所有账号
            List<FollowTraderEntity> followTraderList = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getServerId, vpsId));
            //根据vpsId账号分组
            Map<Integer, List<FollowTraderEntity>> map = followTraderList.stream().collect(Collectors.groupingBy(FollowTraderEntity::getServerId));
            //查询所有平台
            List<FollowPlatformEntity> platformList = followPlatformService.list();
            Map<Long, List<FollowPlatformEntity>> platformMap = platformList.stream().collect(Collectors.groupingBy(FollowPlatformEntity::getId));
            String key = "VPS:PUSH:";
            map.forEach((k, v) -> {
                //多线程写
                boolean flag = redisUtil.setnx(key + k, k, 2);
                //设置成功过表示超过2秒内
                if (flag) {
                    List<AccountCacheVO> accounts = new ArrayList<>();
                    CountDownLatch countDownLatch = new CountDownLatch(v.size());
                    //遍历账号获取持仓订单
                    for (FollowTraderEntity h : v) {
                        ThreadPoolUtils.execute(() -> {
                            AccountCacheVO accountCache = FollowTraderConvert.INSTANCE.convertCache(h);
                            List<OrderCacheVO> orderCaches = new ArrayList<>();
                            //根据id
                            String akey = (h.getType() == 0 ? "S" : "F") + h.getId();
                            accountCache.setKey(akey);
                            String group = h.getId() + " " + h.getAccount();
                            accountCache.setGroup(group);
                            String platformType = platformMap.get(Long.valueOf(h.getPlatformId())).get(0).getPlatformType();
                            accountCache.setPlatformType(platformType);
                            //订单信息
                            AbstractApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(h.getId());
                            QuoteClient quoteClient = null;
                            if (ObjectUtil.isEmpty(leaderApiTrader) || ObjectUtil.isEmpty(leaderApiTrader.quoteClient) || !leaderApiTrader.quoteClient.Connected()) {
                                try {
                                    quoteClient = followPlatformService.tologin(h.getAccount(), h.getPassword(), h.getPlatform());
                                } catch (Exception e) {
                                    log.error("推送从redis数据,登录异常:" + e);
                                }
                            } else {
                                quoteClient = leaderApiTrader.quoteClient;
                            }
                            //所有持仓
                            if (ObjectUtil.isNotEmpty(quoteClient)) {
                                Order[] orders = quoteClient.GetOpenedOrders();
                                Map<Op, List<Order>> orderMap = Arrays.stream(orders).collect(Collectors.groupingBy(order -> order.Type));
                                accountCache.setLots(0.00);
                                accountCache.setCount(0);
                                accountCache.setBuy(0);
                                accountCache.setSell(0);
                                accountCache.setProfit(0.00);
                                orderMap.forEach((a, b) -> {
                                    switch (a) {
                                        case Buy:
                                            accountCache.setBuy(ObjectUtil.isEmpty(b) ? 0 : b.size());
                                            break;
                                        case Sell:
                                            accountCache.setSell(ObjectUtil.isEmpty(b) ? 0 : b.size());
                                            break;
                                        default:
                                            Integer count = ObjectUtil.isEmpty(b) ? 0 : b.size();
                                            accountCache.setCount(accountCache.getCount() + count);
                                            break;
                                    }
                                    if (ObjectUtil.isNotEmpty(b)) {
                                        b.forEach(x -> {
                                            OrderCacheVO orderCacheVO = new OrderCacheVO();
                                            //  orderCacheVO.setId(x.);
                                            //    orderCacheVO.setLogin(x.);
                                            orderCacheVO.setTicket(x.Ticket);
                                            orderCacheVO.setOpenTime(x.OpenTime);
                                            orderCacheVO.setCloseTime(x.CloseTime);
                                            orderCacheVO.setType(x.Type);
                                            orderCacheVO.setLots(x.Lots);
                                            orderCacheVO.setSymbol(x.Symbol);
                                            orderCacheVO.setOpenPrice(x.OpenPrice);
                                            orderCacheVO.setStopLoss(x.StopLoss);
                                            orderCacheVO.setTakeProfit(x.TakeProfit);
                                            orderCacheVO.setClosePrice(x.ClosePrice);
                                            orderCacheVO.setMagicNumber(x.MagicNumber);
                                            orderCacheVO.setSwap(x.Swap);
                                            orderCacheVO.setCommission(x.Commission);
                                            orderCacheVO.setComment(x.Comment);
                                            orderCacheVO.setProfit(x.Profit);
                                            //  orderCacheVO.setPlaceType(h.getUpdater());
                                            orderCaches.add(orderCacheVO);
                                            accountCache.setLots(accountCache.getLots() + x.Lots);
                                            accountCache.setProfit(accountCache.getProfit() + x.Profit);
                                        });
                                    }
                                    accountCache.setOrders(orderCaches);
                                });
                            }
                            accounts.add(accountCache);
                            countDownLatch.countDown();
                        });
                    }
                    try {
                        countDownLatch.await();
                    } catch (InterruptedException e) {
                        log.error("推送从redis数据异常:" + e);
                    }
                    //转出json格式
                    String json = convertJson(accounts);
                    redisUtil.setSlaveRedis(Integer.toString(k), json);
                }
            });
        });
    }

    /**
     * 转成成json
     */
    private String convertJson(List<AccountCacheVO> accounts) {
        //设置从redis数据
        FollowTraderCacheVO cacheVO = new FollowTraderCacheVO();
        cacheVO.setAccounts(accounts);
        cacheVO.setUpdateAt(new Date());
        cacheVO.setStatus(true);
        ObjectMapper objectMapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        //格式化时间格式
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        objectMapper.registerModule(javaTimeModule);
        String json = null;
        try {
            json = objectMapper.writeValueAsString(cacheVO);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return json;
    }
}
