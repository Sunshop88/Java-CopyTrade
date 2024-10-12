package net.maku.mascontrol.websocket;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowOrderSendEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.service.FollowOrderSendService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.impl.FollowOrderSendServiceImpl;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.mascontrol.entity.FollowPlatformEntity;
import net.maku.mascontrol.entity.FollowVarietyEntity;
import net.maku.mascontrol.even.OnQuoteHandler;
import net.maku.mascontrol.service.impl.FollowPlatformServiceImpl;
import net.maku.mascontrol.service.impl.FollowVarietyServiceImpl;
import net.maku.mascontrol.trader.LeaderApiTrader;
import net.maku.mascontrol.trader.LeaderApiTradersAdmin;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.mascontrol.vo.FollowOrderSendSocketVO;
import online.mtapi.mt4.QuoteEventArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
@ServerEndpoint("/socket/trader/orderSend/{traderId}/{symbol}") //此注解相当于设置访问URL
public class TraderOrderSendWebSocket {

    private static final Logger log = LoggerFactory.getLogger(TraderOrderSendWebSocket.class);
    private Session session;

    private String traderId;

    private String symbol;

    private OnQuoteHandler onQuoteHandler;
    private FollowVarietyServiceImpl followVarietyService= SpringContextUtils.getBean( FollowVarietyServiceImpl.class);
    private FollowPlatformServiceImpl followPlatformService= SpringContextUtils.getBean(FollowPlatformServiceImpl.class);
    private FollowTraderServiceImpl followTraderService= SpringContextUtils.getBean( FollowTraderServiceImpl.class);
    /**
     * marketAccountId->SessionSet
     */
    private static Map<String, Set<Session>> sessionPool = new ConcurrentHashMap<>();
    private ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = SpringContextUtils.getBean("scheduledExecutorService", ScheduledThreadPoolExecutor.class);

    private  RedisUtil redisUtil=SpringContextUtils.getBean(RedisUtil.class);;
    private FollowOrderSendService followOrderSendService=SpringContextUtils.getBean(FollowOrderSendServiceImpl.class);
    private LeaderApiTradersAdmin leaderApiTradersAdmin= SpringContextUtils.getBean(LeaderApiTradersAdmin.class);
    @OnOpen
    public void onOpen(Session session, @PathParam(value = "traderId") String traderId, @PathParam(value = "symbol") String symbol) {
        try {
            this.session = session;
            this.traderId = traderId;
            this.symbol = symbol;
            Set<Session> sessionSet;
            if (sessionPool.containsKey(traderId+symbol)) {
                sessionSet = sessionPool.get(traderId+symbol);
            } else {
                sessionSet = new HashSet<>();
            }
            sessionSet.add(session);
            sessionPool.put(traderId+symbol, sessionSet);
            LeaderApiTrader leaderApiTrader =leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId);
            log.info("订阅该品种{}+++{}",symbol,traderId);
            //查询平台信息
            FollowTraderEntity followTraderEntity = followTraderService.getOne(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getId, traderId));
            FollowPlatformEntity followPlatform = followPlatformService.getById(followTraderEntity.getPlatformId());
            //查看品种列表
            List<FollowVarietyEntity> followVarietyEntityList = followVarietyService.list(new LambdaQueryWrapper<FollowVarietyEntity>().eq(FollowVarietyEntity::getBrokerName, followPlatform.getBrokerName()).eq(FollowVarietyEntity::getStdSymbol, symbol));
            if (ObjectUtil.isNotEmpty(followVarietyEntityList)&&ObjectUtil.isNotEmpty(followVarietyEntityList.get(0).getBrokerSymbol())){
                symbol=followVarietyEntityList.get(0).getBrokerSymbol();
            }
            this.symbol = symbol;
            try {
                leaderApiTrader.quoteClient.Subscribe(symbol);
            }catch (Exception e) {
                log.error("订阅失败: " + e.getMessage());
                onClose();
                throw new RuntimeException();
            }
            leaderApiTrader.addOnQuoteHandler(new OnQuoteHandler(leaderApiTrader));
            // 设置超时查询机制，避免死循环
            QuoteEventArgs eventArgs = null;
            try {
                // 使用 ExecutorService 和 Future 来处理超时机制
                eventArgs = getQuoteWithTimeout(symbol, 3,leaderApiTrader);  // 设置超时时间为5秒
            } catch (TimeoutException e) {
                log.error("获取报价超时 关闭socket");
                onClose();
                throw new RuntimeException();
            } catch (Exception e) {
                log.error("获取报价失败 关闭socket: " + e.getMessage());
                onClose();
                throw new RuntimeException();
            }
            if (eventArgs != null) {
                //立即查询
                //查看当前账号订单完成进度
                List<FollowOrderSendEntity> list;
                if (ObjectUtil.isEmpty(redisUtil.get(Constant.TRADER_ORDER + leaderApiTrader.getTrader().getId()))){
                    list = followOrderSendService.list(new LambdaQueryWrapper<FollowOrderSendEntity>().eq(FollowOrderSendEntity::getTraderId,leaderApiTrader.getTrader().getId()));
                    redisUtil.set(Constant.TRADER_ORDER + leaderApiTrader.getTrader().getId(),list);
                }else {
                    list = (List<FollowOrderSendEntity>) redisUtil.get(Constant.TRADER_ORDER + leaderApiTrader.getTrader().getId());
                }
                FollowOrderSendSocketVO followOrderSendSocketVO=new FollowOrderSendSocketVO();
                followOrderSendSocketVO.setSellPrice(eventArgs.Bid);
                followOrderSendSocketVO.setBuyPrice(eventArgs.Ask);
                followOrderSendSocketVO.setStatus(CloseOrOpenEnum.OPEN.getValue());
                if (ObjectUtil.isNotEmpty(list)){
                    List<FollowOrderSendEntity> collect = list.stream().filter(o -> o.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue())).collect(Collectors.toList());
                    if (ObjectUtil.isNotEmpty(collect)){
                        //是否存在正在执行 进度
                        FollowOrderSendEntity followOrderSendEntity = collect.get(0);
                        followOrderSendSocketVO.setStatus(followOrderSendEntity.getStatus());
                        followOrderSendSocketVO.setScheduleNum(followOrderSendEntity.getTotalNum());
                        followOrderSendSocketVO.setScheduleSuccessNum(followOrderSendEntity.getSuccessNum());
                    }
                }
                pushMessage(leaderApiTrader.getTrader().getId().toString(),symbol, JsonUtils.toJsonString(followOrderSendSocketVO));
            }
        } catch (Exception e) {
            log.info("连接异常"+e);
            onClose();
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    @OnClose
    public void onClose() {
        try {
            sessionPool.get(traderId+symbol).remove(session);
            LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId);
            log.info("取消订阅该品种{}++++{}",symbol,traderId);
            leaderApiTrader.removeOnQuoteHandler();
            leaderApiTrader.quoteClient.Unsubscribe(symbol);
            // 需要移除监听器时调用
            leaderApiTrader.quoteClient.OnQuote.removeListener(leaderApiTrader.getQuoteHandler());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 服务器端推送消息
     */
    public void pushMessage(String traderId,String symbol, String message) {
        try {
            Set<Session> sessionSet = sessionPool.get(traderId+symbol);
            if (ObjectUtil.isEmpty(sessionSet))return;
            for (Session session : sessionSet) {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.getBasicRemote().sendText(message);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnMessage
    public void onMessage(String message) {
    }

    public Boolean isConnection(String traderId,String symbol) {
        return sessionPool.containsKey(traderId+symbol);
    }

    // 带超时的 GetQuote 查询
    private QuoteEventArgs getQuoteWithTimeout(String symbol, int timeoutSeconds,LeaderApiTrader leaderApiTrader) throws Exception {
        Callable<QuoteEventArgs> task = () -> {
            // 循环查询报价直到获取到结果
            QuoteEventArgs quote;
            while ((quote = leaderApiTrader.quoteClient.GetQuote(symbol)) == null) {
                // 等待新报价，防止CPU过度占用
                Thread.sleep(100);  // 简单的等待，避免空循环消耗过多资源
            }
            return quote;
        };

        Future<QuoteEventArgs> future = scheduledThreadPoolExecutor.submit(task);
        try {
            // 获取报价，并设置超时时间
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);  // 超时后取消任务
            throw e;  // 抛出超时异常
        }
    }
}
