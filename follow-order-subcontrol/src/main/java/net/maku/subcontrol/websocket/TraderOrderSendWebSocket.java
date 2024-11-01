package net.maku.subcontrol.websocket;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import net.maku.followcom.entity.FollowOrderSendEntity;
import net.maku.followcom.entity.FollowSysmbolSpecificationEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.service.FollowOrderSendService;
import net.maku.followcom.service.impl.FollowOrderSendServiceImpl;
import net.maku.followcom.service.impl.FollowSysmbolSpecificationServiceImpl;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowVarietyEntity;
import net.maku.subcontrol.even.OnQuoteHandler;
import net.maku.followcom.service.impl.FollowPlatformServiceImpl;
import net.maku.followcom.service.impl.FollowVarietyServiceImpl;
import net.maku.subcontrol.trader.LeaderApiTrader;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.subcontrol.vo.FollowOrderSendSocketVO;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.QuoteEventArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
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
    private RedisCache redisCache= SpringContextUtils.getBean( RedisCache.class);
    private FollowSysmbolSpecificationServiceImpl followSysmbolSpecificationService= SpringContextUtils.getBean( FollowSysmbolSpecificationServiceImpl.class);

    private static Map<String, Set<Session>> sessionPool = new ConcurrentHashMap<>();

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
            FollowPlatformEntity followPlatform = followTraderService.getPlatForm(Long.valueOf(traderId));
            //获取symbol信息
            List<FollowSysmbolSpecificationEntity> followSysmbolSpecificationEntityList;
            if (ObjectUtil.isNotEmpty(redisCache.get(Constant.SYMBOL_SPECIFICATION + traderId))){
                followSysmbolSpecificationEntityList = (List<FollowSysmbolSpecificationEntity>)redisCache.get(Constant.SYMBOL_SPECIFICATION +traderId);
            }else {
                //查询改账号的品种规格
                followSysmbolSpecificationEntityList = followSysmbolSpecificationService.list(new LambdaQueryWrapper<FollowSysmbolSpecificationEntity>().eq(FollowSysmbolSpecificationEntity::getTraderId, traderId));
                redisCache.set(Constant.SYMBOL_SPECIFICATION+traderId,followSysmbolSpecificationEntityList);
            }

            //查看品种列表
            List<FollowVarietyEntity> listv = followVarietyService.list(new LambdaQueryWrapper<FollowVarietyEntity>().eq(FollowVarietyEntity::getBrokerName, followPlatform.getBrokerName()).eq(FollowVarietyEntity::getStdSymbol,traderId));
            for (FollowVarietyEntity o:listv){
                if (ObjectUtil.isNotEmpty(o.getBrokerSymbol())){
                    //查看品种规格
                    Optional<FollowSysmbolSpecificationEntity> specificationEntity = followSysmbolSpecificationEntityList.stream().filter(fl -> ObjectUtil.equals(o.getBrokerSymbol(), fl.getSymbol())).findFirst();
                    if (specificationEntity.isPresent()){
                        this.symbol=o.getBrokerSymbol();
                        break;
                    }
                }
            }
            QuoteEventArgs eventArgs = null;

            try {
                if (ObjectUtil.isEmpty(leaderApiTrader.quoteClient.GetQuote(this.symbol))){
                    //订阅
                    leaderApiTrader.quoteClient.Subscribe(this.symbol);
                }
                eventArgs = leaderApiTrader.quoteClient.GetQuote(this.symbol);
            }catch (InvalidSymbolException | online.mtapi.mt4.Exception.TimeoutException | ConnectException e) {
                throw new ServerException("获取报价失败,品种不正确,请先配置品种");
            }
            leaderApiTrader.addOnQuoteHandler(new OnQuoteHandler(leaderApiTrader));

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
                //查询持仓订单缓存
                 if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_ACTIVE + traderId))){
                     followOrderSendSocketVO.setOrderActiveInfoList((List<OrderActiveInfoVO>)redisCache.get(Constant.TRADER_ACTIVE + traderId));
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

}
