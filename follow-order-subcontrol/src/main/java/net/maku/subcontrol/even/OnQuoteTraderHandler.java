package net.maku.subcontrol.even;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.Setter;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.impl.FollowPlatformServiceImpl;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.FollowRedisTraderVO;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.cache.RedissonLockUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.vo.FollowOrderActiveSocketVO;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import online.mtapi.mt4.QuoteEventHandler;

import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

public class OnQuoteTraderHandler implements QuoteEventHandler {
    private static final Logger log = LoggerFactory.getLogger(OnQuoteTraderHandler.class);
    protected FollowTraderEntity leader;
    protected AbstractApiTrader abstractApiTrader;
    protected FollowTraderService followTraderService;
    @Setter
    protected Boolean running = Boolean.TRUE;
    protected RedisCache redisCache;
    private RedissonLockUtil redissonLockUtil;
    private FollowPlatformService followPlatformService ;

    private final FollowRedisTraderVO followRedisTraderVO=new FollowRedisTraderVO();
    // 设定时间间隔，单位为毫秒
    private final long interval = 3000; // 1秒间隔

    // 记录上次执行时间
    private long lastInvokeTime = 0;
    // 记录上次执行时间
    private long lastInvokeTimeTrader = 0;
    // 设定时间间隔，单位为毫秒
    private final long intervalTrader = 300000; // 五分钟间隔


    public OnQuoteTraderHandler(AbstractApiTrader abstractApiTrader ) {
        this.abstractApiTrader=abstractApiTrader;
        followTraderService=SpringContextUtils.getBean(FollowTraderServiceImpl.class);
        redisCache=SpringContextUtils.getBean(RedisCache.class);
        this.redissonLockUtil=SpringContextUtils.getBean(RedissonLockUtil.class);
        this.followPlatformService=SpringContextUtils.getBean(FollowPlatformServiceImpl.class);
    }

    public void invoke(Object sender, QuoteEventArgs quote) {
        if (!running) {
            return;
        }
        // 获取当前系统时间
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastInvokeTime  >= interval) {
            try {
//            RLock lock = redissonLockUtil.getLock("LOCK" + Constant.TRADER_USER + abstractApiTrader.getTrader().getId());
//            boolean flag = lock.tryLock(5, TimeUnit.SECONDS);
//            if (flag) {
                // 更新该symbol的上次执行时间为当前时间
                lastInvokeTime= currentTime;
               QuoteClient qc = (QuoteClient) sender;
                //测试
//               FollowPlatformEntity followPlatformServiceOne = followPlatformService.getOne(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, abstractApiTrader.getTrader().getPlatform()));
//                String serverNode = followPlatformServiceOne.getServerNode();
//                String[] split = serverNode.split(":");
//                QuoteClient  qc = new QuoteClient(Integer.parseInt(abstractApiTrader.getTrader().getAccount()), abstractApiTrader.getTrader().getPassword(), quoteClient.Host, quoteClient.Port);
//                qc.Connect();
//                log.info("quoteClient代理对象{}",quoteClient);
             //   cacheManager.removeCache(qc.GetOpenedOrders());
                //缓存经常变动的三个值信息
                followRedisTraderVO.setTraderId(abstractApiTrader.getTrader().getId());
                followRedisTraderVO.setBalance(BigDecimal.valueOf(qc.AccountBalance()));
                followRedisTraderVO.setProfit(BigDecimal.valueOf(qc.Profit));
                followRedisTraderVO.setEuqit(BigDecimal.valueOf(qc.AccountEquity()));
                followRedisTraderVO.setFreeMargin(BigDecimal.valueOf(qc.FreeMargin));
                if (BigDecimal.valueOf(qc.AccountMargin()).compareTo(BigDecimal.ZERO) != 0) {
                    followRedisTraderVO.setMarginProportion(BigDecimal.valueOf(qc.AccountEquity()).divide(BigDecimal.valueOf(qc.AccountMargin()),4, RoundingMode.HALF_UP));
                }else {
                    followRedisTraderVO.setMarginProportion(BigDecimal.ZERO);
                }
                Order[] orders = qc.GetOpenedOrders();
                List<Order> openedOrders = Arrays.stream(orders).filter(order -> order.Type == Buy || order.Type == Sell).toList();
                int count =  openedOrders.size();
            //    log.info("{}-MT4,订单数量{},持仓数据：{}",abstractApiTrader.getTrader().getAccount(),count);
                List<OrderActiveInfoVO> orderActiveInfoList = converOrderActive(openedOrders, abstractApiTrader.getTrader().getAccount());
                FollowOrderActiveSocketVO followOrderActiveSocketVO = new FollowOrderActiveSocketVO();
                followOrderActiveSocketVO.setOrderActiveInfoList(orderActiveInfoList);
                //存入redis
                redisCache.set(Constant.TRADER_ACTIVE + abstractApiTrader.getTrader().getId(), JSONObject.toJSON(orderActiveInfoList));
                followRedisTraderVO.setTotal(count);
                log.info("{}写入redis数据订单量{}",abstractApiTrader.getTrader().getAccount(),count);
                followRedisTraderVO.setBuyNum(Arrays.stream(orders).filter(order ->order.Type == Buy).mapToDouble(order->order.Lots).sum());
                followRedisTraderVO.setSellNum(Arrays.stream(orders).filter(order ->order.Type == Sell).mapToDouble(order->order.Lots).sum());
                //设置缓存
                followRedisTraderVO.setMargin(qc.Margin);
                followRedisTraderVO.setCredit(qc.Credit);
                followRedisTraderVO.setConnectTrader(qc.Host+":"+qc.Port);
                redisCache.set(Constant.TRADER_USER+abstractApiTrader.getTrader().getId(),followRedisTraderVO);
//                qc.Disconnect();
//                }else {
//                    invoke(sender,quote);
//                }
            } catch (Exception e) {
                System.err.println("Error during quote processing: " + e.getMessage());
                e.printStackTrace();
            }
        }
        // 判断当前时间与上次执行时间的间隔是否达到设定的间隔时间
        if (currentTime - lastInvokeTimeTrader >= intervalTrader) {
            // 更新上次执行时间为当前时间
            lastInvokeTimeTrader = currentTime;
            updateTraderInfo();
        }
    }

    /**
     * 更新 FollowTraderEntity 信息，每5分钟执行一次
     */
    private void updateTraderInfo() {
        try {
            FollowRedisTraderVO followRedisTraderVO = (FollowRedisTraderVO) redisCache.get(Constant.TRADER_USER + abstractApiTrader.getTrader().getId());
            if (ObjectUtil.isNotEmpty(followRedisTraderVO)){
                log.info("每5分钟更新一次数据库 traderId: {}", abstractApiTrader.getTrader().getId());
                followTraderService.update(Wrappers.<FollowTraderEntity>lambdaUpdate()
                        .eq(FollowTraderEntity::getId, abstractApiTrader.getTrader().getId())
                        .set(FollowTraderEntity::getEuqit, followRedisTraderVO.getEuqit())
                        .set(FollowTraderEntity::getMarginProportion,followRedisTraderVO.getMarginProportion())
                        .set(FollowTraderEntity::getBalance, followRedisTraderVO.getBalance()));
            }
        } catch (Exception e) {
            log.error("Error updating trader info: {}", e.getMessage(), e);
        }
    }


    private List<OrderActiveInfoVO> converOrderActive(List<Order> openedOrders, String account) {
        List<OrderActiveInfoVO> collect = new ArrayList<>();
        for (Order o : openedOrders) {
            OrderActiveInfoVO reusableOrderActiveInfoVO = new OrderActiveInfoVO(); // 从对象池中借用对象
            resetOrderActiveInfoVO(reusableOrderActiveInfoVO, o, account); // 重用并重置对象
            collect.add(reusableOrderActiveInfoVO);
        }

        //倒序返回
        return collect.stream()
                .sorted(Comparator.comparing(OrderActiveInfoVO::getOpenTime).reversed())
                .collect(Collectors.toList());
    }
    private void resetOrderActiveInfoVO(OrderActiveInfoVO vo, Order order, String account) {
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
        //增加五小时
        vo.setOpenTime(DateUtil.toLocalDateTime(DateUtil.offsetHour(DateUtil.date(order.OpenTime), 0)));
        // vo.setOpenTime(order.OpenTime);
        vo.setStopLoss(order.StopLoss);
        vo.setTakeProfit(order.TakeProfit);
    }

}
