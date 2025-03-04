package net.maku.subcontrol.even;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.*;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.*;
import net.maku.followcom.service.impl.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.AccountCacheVO;
import net.maku.followcom.vo.FollowTraderCacheVO;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.followcom.vo.OrderCacheVO;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.cache.RedissonLockUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.service.FollowOrderHistoryService;
import net.maku.subcontrol.service.impl.FollowOrderHistoryServiceImpl;
import net.maku.subcontrol.trader.*;
import net.maku.subcontrol.trader.strategy.*;
import net.maku.subcontrol.vo.FollowOrderActiveSocketVO;
import online.mtapi.mt4.*;
import cn.hutool.core.date.DateUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;
import static online.mtapi.mt4.UpdateAction.*;

/**
 * mtapi.online 监听MT4账户订单变化
 *
 * @author samson bruce
 */
@Slf4j
public class LeaderOrderUpdateEventHandlerImpl extends OrderUpdateHandler {
    private LeaderApiTradersAdmin leaderApiTradersAdmin = SpringContextUtils.getBean(LeaderApiTradersAdmin.class);
    private RedisUtil redisUtil = SpringContextUtils.getBean(RedisUtil.class);
    protected FollowOrderHistoryService followOrderHistoryService;
    protected FollowVarietyService followVarietyService;
    protected FollowTraderService followTraderService;
    protected FollowPlatformService followPlatformService;
    protected FollowVpsService followVpsService;
    protected FollowTraderLogService followTraderLogService;
    private CopierApiTradersAdmin copierApiTradersAdmin = SpringContextUtils.getBean(CopierApiTradersAdmin.class);
    private final Map<AcEnum, IOperationStrategy> strategyMap;
    private RedissonLockUtil redissonLockUtil=SpringContextUtils.getBean(RedissonLockUtil.class);
    // 上次执行时间
    private long lastInvokeTime = 0;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingMessages = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(0, Thread.ofVirtual().factory());

    // 设定时间间隔，单位为毫秒
    private final long interval = 1000; // 1秒间隔
    public LeaderOrderUpdateEventHandlerImpl(AbstractApiTrader abstractApiTrader) {
        super();
        this.abstractApiTrader = abstractApiTrader;
        this.leader = this.abstractApiTrader.getTrader();
        this.followOrderHistoryService = SpringContextUtils.getBean(FollowOrderHistoryServiceImpl.class);
        this.followVarietyService = SpringContextUtils.getBean(FollowVarietyServiceImpl.class);
        this.followTraderService = SpringContextUtils.getBean(FollowTraderServiceImpl.class);
        this.followPlatformService = SpringContextUtils.getBean(FollowPlatformServiceImpl.class);
        this.followVpsService = SpringContextUtils.getBean(FollowVpsServiceImpl.class);
        this.followTraderLogService = SpringContextUtils.getBean(FollowTraderLogServiceImpl.class);
        strategyMap = new HashMap<>();
        strategyMap.put(AcEnum.MO, SpringContextUtils.getBean(OrderSendMaster.class));
        strategyMap.put(AcEnum.MC, SpringContextUtils.getBean(OrderCloseMaster.class));
        strategyMap.put(AcEnum.NEW, SpringContextUtils.getBean(OrderSendCopier.class));
        strategyMap.put(AcEnum.CLOSED, SpringContextUtils.getBean(OrderCloseCopier.class));

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
        //避免重复监听
        String val=Constant.FOLLOW_ON_EVEN + FollowConstant.LOCAL_HOST + "#" + orderUpdateEventArgs.Action + "#" + order.Ticket;
        // 使用 Redis 原子操作 SET NX EX
        boolean isLocked = redisUtil.setnx(val, 1, 36000); // 原子操作
        if (!isLocked) {
            log.info(order.Ticket + "锁定监听重复");
            return;
        }
        //发送websocket消息标识
        if (orderUpdateEventArgs.Action == UpdateAction.PositionClose||orderUpdateEventArgs.Action == UpdateAction.PositionOpen||orderUpdateEventArgs.Action == UpdateAction.PendingFill) {
            ScheduledFuture<?> existingTask = pendingMessages.get(leader.getId().toString());
            if (existingTask != null) {
                existingTask.cancel(false); // 不强制中断，允许任务自然结束
            }

            // 新建延迟任务，等待 100ms 后发送消息
            ScheduledFuture<?> newTask = scheduler.schedule(() -> {
                List<Order> openedOrders = Arrays.stream(abstractApiTrader.quoteClient.GetOpenedOrders()).filter(order1 ->(order1.Type == Buy || order1.Type == Sell)).collect(Collectors.toList());
                FollowOrderActiveSocketVO followOrderActiveSocketVO = new FollowOrderActiveSocketVO();
                followOrderActiveSocketVO.setOrderActiveInfoList(convertOrderActive(openedOrders,leader.getAccount()));
                log.info("发送消息"+leader.getId());
                traderOrderActiveWebSocket.pushMessage(leader.toString(),"0", JsonUtils.toJsonString(followOrderActiveSocketVO));
                pendingMessages.remove(leader.getId().toString());
            }, 50, TimeUnit.MILLISECONDS);
            // 更新缓存中的任务
            pendingMessages.put(leader.getId().toString(), newTask);
        }

        switch (orderUpdateEventArgs.Action) {
            case PositionOpen:
            case PendingFill:
                log.info("[MT4喊单者：{}-{}-{}]监听到" + orderUpdateEventArgs.Action + ",订单信息[{}]", leader.getId(), leader.getAccount(), leader.getServerName(), new EaOrderInfo(order));
                ThreadPoolUtils.getExecutor().execute(()->{
                    double equity = 0.0;
                    try {
                        equity = abstractApiTrader.quoteClient.AccountEquity();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    EaOrderInfo eaOrderInfo = send2Copiers(OrderChangeTypeEnum.NEW, order, equity, currency, LocalDateTime.now());
                    //喊单开仓
                    //查找vps状态
                    Integer serverId = leader.getServerId();
                    //发送MT4处理请求
                    strategyMap.get(AcEnum.MO).operate(abstractApiTrader, eaOrderInfo, 0);
                });
                flag = 1;
                //推送到redis
//                pushCache(leader.getServerId());
                break;
            case PositionClose:
                log.info("[MT4喊单者：{}-{}-{}]监听到" + orderUpdateEventArgs.Action + ",订单信息[{}]", leader.getId(), leader.getAccount(), leader.getServerName(), new EaOrderInfo(order));
                ThreadPoolUtils.getExecutor().execute(()->{
                    EaOrderInfo eaOrderInfo = send2Copiers(OrderChangeTypeEnum.CLOSED, order, 0, currency, LocalDateTime.now());
                    //喊单平仓
                    //发送MT4处理请求
                    strategyMap.get(AcEnum.MC).operate(abstractApiTrader, eaOrderInfo, 0);
                });
                flag = 1;
                //推送到redis
//                pushCache(leader.getServerId());
                break;
            default:
                log.error("Unexpected value: " + orderUpdateEventArgs.Action);
        }
        int finalFlag = flag;
        ThreadPoolUtils.getExecutor().execute(()->{
            if (finalFlag == 1) {
                //查看喊单账号是否开启跟单
                if (leader.getFollowStatus().equals(CloseOrOpenEnum.OPEN.getValue())) {
                    //查询订阅关系
                    List<FollowTraderSubscribeEntity> followTraderSubscribeEntityList = followTraderSubscribeService.getSubscribeOrder(leader.getId());
                    if (followVpsService.getVps(FollowConstant.LOCAL_HOST).getIsSyn().equals(CloseOrOpenEnum.OPEN.getValue())) {
                        followTraderSubscribeEntityList.forEach(o -> {
                            ThreadPoolUtils.getExecutor().execute(() -> {
                                String slaveId = o.getSlaveId().toString();
                                if (o.getFollowStatus().equals(CloseOrOpenEnum.CLOSE.getValue())) {
                                    log.info(order.Ticket+"未开通跟单状态");
                                    return;
                                }
                                if (orderUpdateEventArgs.Action == PositionClose && o.getFollowClose().equals(CloseOrOpenEnum.CLOSE.getValue())) {
                                    log.info(order.Ticket+":"+slaveId+"未开通跟单平仓状态");
                                    return;
                                }
                                if ((orderUpdateEventArgs.Action == PositionOpen || orderUpdateEventArgs.Action == PendingFill) && o.getFollowOpen().equals(CloseOrOpenEnum.CLOSE.getValue())) {
                                    log.info(order.Ticket+":"+slaveId+"未开通跟单下单状态");
                                    return;
                                }
                                // 构造订单信息并发布
                                EaOrderInfo eaOrderInfo = send2Copiers(OrderChangeTypeEnum.NEW, order, 0, currency, LocalDateTime.now());
                                eaOrderInfo.setSlaveId(slaveId);
                                CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(slaveId);
                                if (ObjectUtil.isEmpty(copierApiTrader)) {
                                    log.info("开平重连" + slaveId);
                                    copierApiTradersAdmin.removeTrader(slaveId);
                                    FollowTraderEntity slaveTrader = followTraderService.getById(slaveId);
                                    ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(slaveTrader);
                                    if (conCodeEnum == ConCodeEnum.SUCCESS) {
                                        CopierApiTrader copierApiTrader1 = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(slaveId);
                                        copierApiTrader1.setTrader(slaveTrader);
                                        copierApiTrader1.startTrade();
                                    }else if (conCodeEnum == ConCodeEnum.AGAIN){
                                        long maxWaitTimeMillis = 10000; // 最多等待10秒
                                        long startTime = System.currentTimeMillis();
                                        CopierApiTrader copierApiTrader1 = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(slaveId);
                                        // 开始等待直到获取到copierApiTrader1
                                        while (copierApiTrader1 == null && (System.currentTimeMillis() - startTime) < maxWaitTimeMillis) {
                                            try {
                                                // 每次自旋等待500ms后再检查
                                                Thread.sleep(500);
                                            } catch (InterruptedException e) {
                                                // 处理中断
                                                Thread.currentThread().interrupt();
                                                break;
                                            }
                                            copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(slaveId);
                                        }
                                        //重复提交
                                        if (ObjectUtil.isNotEmpty(copierApiTrader)){
                                            log.info(slaveId+"重复提交并等待完成");
                                        }else {
                                            log.info(slaveId+"重复提交并等待失败");
                                        }
                                    }
                                }
                                if (orderUpdateEventArgs.Action == PositionClose) {
                                    //跟单平仓
                                    //发送MT4处理请求
                                    log.info(order.Ticket+"发送平仓请求" + slaveId);
                                    Integer serverId = leader.getServerId();
                                    FollowVpsEntity vps = followVpsService.getById(serverId);
                                    if (ObjectUtil.isNotEmpty(vps) && !vps.getIsActive().equals(CloseOrOpenEnum.CLOSE.getValue())) {
                                        log.info(order.Ticket+"开始平仓请求" + slaveId);
                                        strategyMap.get(AcEnum.CLOSED).operate(copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(slaveId), eaOrderInfo, 0);
                                    }
                                } else {
                                    //跟单开仓
                                    //发送MT4处理请求
                                    log.info(order.Ticket+"发送下单请求" + slaveId);
                                    Integer serverId = leader.getServerId();
                                    FollowVpsEntity vps = followVpsService.getById(serverId);
                                    if (ObjectUtil.isNotEmpty(vps) && !vps.getIsActive().equals(CloseOrOpenEnum.CLOSE.getValue())) {
                                        log.info(order.Ticket+"开始下单请求" + slaveId);
                                        strategyMap.get(AcEnum.NEW).operate(copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(slaveId), eaOrderInfo, 0);
                                    }
                                }
                            });
                        });
                    } else {
                        followTraderSubscribeEntityList.forEach(o -> {
                            String slaveId = o.getSlaveId().toString();
                            if (o.getFollowStatus().equals(CloseOrOpenEnum.CLOSE.getValue())) {
                                log.info(order.Ticket+"未开通跟单状态");
                                return;
                            }
                            if (orderUpdateEventArgs.Action == PositionClose && o.getFollowClose().equals(CloseOrOpenEnum.CLOSE.getValue())) {
                                log.info(order.Ticket+":"+slaveId+"未开通跟单平仓状态");
                                return;
                            }
                            if ((orderUpdateEventArgs.Action == PositionOpen || orderUpdateEventArgs.Action == PendingFill) && o.getFollowOpen().equals(CloseOrOpenEnum.CLOSE.getValue())) {
                                log.info(order.Ticket+":"+slaveId+"未开通跟单下单状态");
                                return;
                            }
                            // 构造订单信息并发布
                            EaOrderInfo eaOrderInfo = send2Copiers(OrderChangeTypeEnum.NEW, order, 0, currency, LocalDateTime.now());
                            eaOrderInfo.setSlaveId(slaveId);
                            CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(slaveId);
                            if (ObjectUtil.isEmpty(copierApiTrader)) {
                                log.info("开平重连" + slaveId);
                                copierApiTradersAdmin.removeTrader(slaveId);
                                FollowTraderEntity slaveTrader = followTraderService.getById(slaveId);
                                ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(slaveTrader);
                                if (conCodeEnum == ConCodeEnum.SUCCESS) {
                                    CopierApiTrader copierApiTrader1 = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(slaveId);
                                    copierApiTrader1.setTrader(slaveTrader);
                                    copierApiTrader1.startTrade();
                                }else if (conCodeEnum == ConCodeEnum.AGAIN){
                                    long maxWaitTimeMillis = 10000; // 最多等待10秒
                                    long startTime = System.currentTimeMillis();
                                    CopierApiTrader copierApiTrader1 = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(slaveId);
                                    // 开始等待直到获取到copierApiTrader1
                                    while (copierApiTrader1 == null && (System.currentTimeMillis() - startTime) < maxWaitTimeMillis) {
                                        try {
                                            // 每次自旋等待500ms后再检查
                                            Thread.sleep(500);
                                        } catch (InterruptedException e) {
                                            // 处理中断
                                            Thread.currentThread().interrupt();
                                            break;
                                        }
                                        copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(slaveId);
                                    }
                                    //重复提交
                                    if (ObjectUtil.isNotEmpty(copierApiTrader)){
                                        log.info(slaveId+"重复提交并等待完成");
                                    }else {
                                        log.info(slaveId+"重复提交并等待失败");
                                    }
                                }
                            }
                            if (orderUpdateEventArgs.Action == PositionClose) {
                                //跟单平仓
                                //发送MT4处理请求
                                log.info(order.Ticket+"发送平仓请求" + slaveId);
                                Integer serverId = leader.getServerId();
                                FollowVpsEntity vps = followVpsService.getById(serverId);
                                if (ObjectUtil.isNotEmpty(vps) && !vps.getIsActive().equals(CloseOrOpenEnum.CLOSE.getValue())) {
                                    log.info(order.Ticket+"开始平仓请求" + slaveId);
                                    strategyMap.get(AcEnum.CLOSED).operate(copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(slaveId), eaOrderInfo, 0);
                                }
                            } else {
                                //跟单开仓
                                //发送MT4处理请求
                                log.info(order.Ticket+"发送下单请求" + slaveId);
                                Integer serverId = leader.getServerId();
                                FollowVpsEntity vps = followVpsService.getById(serverId);
                                if (ObjectUtil.isNotEmpty(vps) && !vps.getIsActive().equals(CloseOrOpenEnum.CLOSE.getValue())) {
                                    log.info(order.Ticket+"开始下单请求" + slaveId);
                                    strategyMap.get(AcEnum.NEW).operate(copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(slaveId), eaOrderInfo, 0);
                                }
                            }
                        });
                    }
                } else {
                    //喊单账号未开启
                    log.info(leader.getId() + "喊单账号状态未开启");
                }

                //发送消息 注释推送
                traderOrderActiveWebSocket.sendPeriodicMessage(leader.getId().toString(), "0");
                //保存历史数据
                //   followOrderHistoryService.saveOrderHistory(abstractApiTrader.quoteClient, leader,DateUtil.toLocalDateTime(DateUtil.offsetDay(DateUtil.date(),-5)));
            }
        });
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
                            AbstractApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(h.getId().toString());
                            QuoteClient quoteClient = null;
                            if (ObjectUtil.isEmpty(leaderApiTrader) || ObjectUtil.isEmpty(leaderApiTrader.quoteClient) || !leaderApiTrader.quoteClient.Connected()) {
                                try {
                                    leaderApiTradersAdmin.removeTrader(h.getId().toString());
                                    ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(h);
                                    if (conCodeEnum == ConCodeEnum.SUCCESS) {
                                        quoteClient=leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(h.getId().toString()).quoteClient;
                                        LeaderApiTrader leaderApiTrader1 = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(h.getId().toString());
                                        leaderApiTrader1.startTrade();
                                    } else if (conCodeEnum == ConCodeEnum.AGAIN){
                                        long maxWaitTimeMillis = 10000; // 最多等待10秒
                                        long startTime = System.currentTimeMillis();
                                        leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(h.getId().toString());
                                        // 开始等待直到获取到copierApiTrader1
                                        while (leaderApiTrader == null && (System.currentTimeMillis() - startTime) < maxWaitTimeMillis) {
                                            try {
                                                // 每次自旋等待500ms后再检查
                                                Thread.sleep(500);
                                            } catch (InterruptedException e) {
                                                // 处理中断
                                                Thread.currentThread().interrupt();
                                                break;
                                            }
                                            leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(h.getId().toString());
                                        }
                                        //重复提交
                                        if (ObjectUtil.isNotEmpty(leaderApiTrader)){
                                            log.info(h.getId().toString()+"重复提交并等待完成");
                                            quoteClient = leaderApiTrader.quoteClient;
                                        }else {
                                            log.info(h.getId()+"重复提交并等待失败");
                                        }
                                    }else {
                                        log.error("登录异常");
                                    }
                                } catch (Exception e) {
                                    log.error("推送从redis数据,登录异常:" + e);
                                }
                            } else {
                                quoteClient = leaderApiTrader.quoteClient;

                            }
                            //所有持仓
                            if (ObjectUtil.isNotEmpty(quoteClient)) {
                                Order[] orders = quoteClient.GetOpenedOrders();
                                //账号信息
                                ConGroup account = quoteClient.Account();
                                accountCache.setCredit(account.credit);
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
//                    redisUtil.setSlaveRedis(Integer.toString(k), json);
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


    private List<OrderActiveInfoVO> convertOrderActive(List<Order> openedOrders, String account) {
        return openedOrders.stream()
                .map(order -> createOrderActiveInfoVO(order, account))
                .sorted(Comparator.comparing(OrderActiveInfoVO::getOpenTime).reversed())
                .collect(Collectors.toList());
    }

    private OrderActiveInfoVO createOrderActiveInfoVO(Order order, String account) {
        OrderActiveInfoVO vo = new OrderActiveInfoVO();
        vo.setAccount(account);
        vo.setLots(order.Lots);
        vo.setComment(order.Comment);
        vo.setOrderNo(order.Ticket);
        vo.setCommission(order.Commission);
        vo.setSwap(order.Swap);
        vo.setProfit(order.Profit);
        vo.setSymbol(order.Symbol);
        vo.setOpenPrice(order.OpenPrice);
        vo.setMagicNumber(order.MagicNumber);
        vo.setType(order.Type.name());
        // 增加五小时
        vo.setOpenTime(DateUtil.toLocalDateTime(DateUtil.offsetHour(DateUtil.date(order.OpenTime), 0)));
        vo.setStopLoss(order.StopLoss);
        vo.setTakeProfit(order.TakeProfit);
        return vo;
    }
}
