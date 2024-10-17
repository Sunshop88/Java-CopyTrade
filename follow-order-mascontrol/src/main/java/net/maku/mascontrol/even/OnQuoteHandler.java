package net.maku.mascontrol.even;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import net.maku.followcom.entity.FollowOrderSendEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.service.FollowOrderSendService;
import net.maku.followcom.service.FollowSubscribeOrderService;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.mascontrol.trader.AbstractApiTrader;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.mascontrol.vo.FollowOrderSendSocketVO;
import net.maku.mascontrol.vo.OrderActiveInfoVO;
import net.maku.mascontrol.websocket.TraderOrderSendWebSocket;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import online.mtapi.mt4.QuoteEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

public class OnQuoteHandler implements QuoteEventHandler {
    private static final Logger log = LoggerFactory.getLogger(OnQuoteHandler.class);
    protected FollowTraderEntity leader;
    protected AbstractApiTrader abstractApiTrader;

    protected FollowSubscribeOrderService followSubscribeOrderService;

    protected Boolean running = Boolean.TRUE;

    private TraderOrderSendWebSocket traderOrderSendWebSocket;
    private FollowOrderSendService followOrderSendService;
    private RedisCache redisCache;
    private final FollowOrderSendSocketVO followOrderSendSocketVO = new FollowOrderSendSocketVO();

    // 用于存储每个Symbol的锁
    private static final ConcurrentHashMap<String, Lock> symbolLockMap = new ConcurrentHashMap<>();
    private final OrderActiveInfoVOPool orderActiveInfoVOPool = new OrderActiveInfoVOPool();
    private final ScheduledThreadPoolExecutor scheduler = ThreadPoolUtils.getScheduledExecute();

    // 设定时间间隔，单位为毫秒
    private final long interval = 3000; // 3秒间隔
    // 用于存储每个 symbol 上次执行时间
    private static final ConcurrentHashMap<String, Long> symbolLastInvokeTimeMap = new ConcurrentHashMap<>();
    private final List<OrderActiveInfoVO> pendingReturnObjects = new ArrayList<>();

    public OnQuoteHandler(AbstractApiTrader abstractApiTrader ) {
        this.abstractApiTrader=abstractApiTrader;
        this.followSubscribeOrderService = SpringContextUtils.getBean(FollowSubscribeOrderService.class);
        traderOrderSendWebSocket=SpringContextUtils.getBean(TraderOrderSendWebSocket.class);
        followOrderSendService=SpringContextUtils.getBean(FollowOrderSendService.class);
        redisCache=SpringContextUtils.getBean(RedisCache.class);
    }


    public void invoke(Object sender, QuoteEventArgs quote) {

        // 判断当前时间与上次执行时间的间隔是否达到设定的间隔时间

        // 获取当前系统时间
        long currentTime = System.currentTimeMillis();
        // 获取该symbol上次执行时间
        long lastSymbolInvokeTime = symbolLastInvokeTimeMap.getOrDefault(quote.Symbol, 0L);
        if (currentTime - lastSymbolInvokeTime  >= interval) {
            // 回收对象
            returnObjectsInBatch();
            // 更新该symbol的上次执行时间为当前时间
            symbolLastInvokeTimeMap.put(quote.Symbol, currentTime);
            QuoteClient qc = (QuoteClient) sender;
            try {
                handleQuote(qc, quote);
            } catch (Exception e) {
                System.err.println("Error during quote processing: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void handleQuote(QuoteClient qc, QuoteEventArgs quote) {
        //所有持仓
        List<Order> openedOrders = Arrays.stream(qc.GetOpenedOrders()).filter(order -> order.Type == Buy || order.Type == Sell).collect(Collectors.toList());
        //账户信息
        log.info("OnQuote监听：" +abstractApiTrader.getTrader().getId()+ quote.Symbol+quote.Bid+"dd"+quote.Ask);
        List<OrderActiveInfoVO> orderActiveInfoList=converOrderActive(openedOrders,abstractApiTrader.getTrader().getAccount());

        List<FollowOrderSendEntity> list;
        if (ObjectUtil.isEmpty(redisCache.get(Constant.TRADER_ORDER + abstractApiTrader.getTrader().getId()))){
            list = followOrderSendService.list(new LambdaQueryWrapper<FollowOrderSendEntity>().eq(FollowOrderSendEntity::getTraderId,abstractApiTrader.getTrader().getId()));
            redisCache.set(Constant.TRADER_ORDER + abstractApiTrader.getTrader().getId(),list);
        }else {
            list = (List<FollowOrderSendEntity>) redisCache.get(Constant.TRADER_ORDER + abstractApiTrader.getTrader().getId());
        }
        //查看当前账号订单完成进度
        followOrderSendSocketVO.setSellPrice(quote.Bid);
        followOrderSendSocketVO.setBuyPrice(quote.Ask);
        followOrderSendSocketVO.setStatus(CloseOrOpenEnum.OPEN.getValue());
        followOrderSendSocketVO.setOrderActiveInfoList(orderActiveInfoList);

        if (ObjectUtil.isNotEmpty(list)) {
            FollowOrderSendEntity followOrderSendEntity = list.stream()
                    .filter(o -> o.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue()))
                    .findFirst().orElse(null);
            if (followOrderSendEntity != null) {
                followOrderSendSocketVO.setStatus(followOrderSendEntity.getStatus());
                followOrderSendSocketVO.setScheduleNum(followOrderSendEntity.getTotalNum());
                followOrderSendSocketVO.setScheduleSuccessNum(followOrderSendEntity.getSuccessNum());
                followOrderSendSocketVO.setScheduleFailNum(followOrderSendEntity.getFailNum());
            }
        }
        //存入redis
        redisCache.set(Constant.TRADER_ACTIVE+abstractApiTrader.getTrader().getId().toString(),orderActiveInfoList);
        traderOrderSendWebSocket.pushMessage(abstractApiTrader.getTrader().getId().toString(),quote.Symbol, JsonUtils.toJsonString(followOrderSendSocketVO));
    }

    private List<OrderActiveInfoVO> converOrderActive(List<Order> openedOrders, String account) {
        List<OrderActiveInfoVO> collect = new ArrayList<>();
        for (Order o : openedOrders) {
            OrderActiveInfoVO reusableOrderActiveInfoVO = orderActiveInfoVOPool.borrowObject(); // 从对象池中借用对象
            resetOrderActiveInfoVO(reusableOrderActiveInfoVO, o, account); // 重用并重置对象
            collect.add(reusableOrderActiveInfoVO);
        }
        // 将所有借用的对象添加到待归还列表中
        synchronized (pendingReturnObjects) {
            pendingReturnObjects.addAll(collect);
        }
        return collect;
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
        vo.setOpenTime(DateUtil.toLocalDateTime(DateUtil.offsetHour(DateUtil.date(order.OpenTime),5)));
        vo.setStopLoss(order.StopLoss);
        vo.setTakeProfit(order.TakeProfit);
    }

    private void returnObjectsInBatch() {
        synchronized (pendingReturnObjects) {
            if (pendingReturnObjects.isEmpty()) {
                return; // 如果没有待归还的对象，直接返回
            }

            // 归还所有对象
            for (OrderActiveInfoVO vo : pendingReturnObjects) {
                orderActiveInfoVOPool.returnObject(vo);
            }

            // 清空待归还列表
            pendingReturnObjects.clear();
        }
    }

}
