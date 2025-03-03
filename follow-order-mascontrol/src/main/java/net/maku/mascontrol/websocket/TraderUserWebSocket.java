package net.maku.mascontrol.websocket;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import net.maku.followcom.entity.FollowOrderDetailEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderUserEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderCloseEnum;
import net.maku.followcom.service.FollowOrderDetailService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderUserService;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.BargainAccountVO;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.rmi.ServerException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static dm.jdbc.util.DriverUtil.log;

/**
 * Author:  zsd
 * Date:  2025/2/28/周五 18:57
 */
@Component
@ServerEndpoint("/socket/traderUser")
public class TraderUserWebSocket {
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private Map<String, ScheduledFuture<?>> scheduledFutureMap = new HashMap<>();
    private RedisCache redisCache = SpringContextUtils.getBean(RedisCache.class);
    private final FollowTraderUserService followTraderUserService= SpringContextUtils.getBean(FollowTraderUserService.class);
    private final FollowTraderService followTraderService=SpringContextUtils.getBean(FollowTraderService.class);
    private final FollowOrderDetailService followOrderDetailService=SpringContextUtils.getBean(FollowOrderDetailService.class);
    @OnOpen
    public void onOpen(Session session) throws IOException {
    }
    // 当接收到客户端的消息时调用
    @OnMessage
    public void onMessage(String message, Session session) throws ServerException {
        String id = session.getId();
        ScheduledFuture<?> st = scheduledFutureMap.get(id);
        if (st != null) {
            st.cancel(true);
        }
        ScheduledFuture scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(() -> {
        JSONObject jsonObj = JSONObject.parseObject(message);
        Long traderUserId = jsonObj.getLong("traderUserId");
        //选中当前账号的持仓
        if(ObjectUtil.isNotEmpty(traderUserId)) {
            List<OrderActiveInfoVO> active = getActive(traderUserId);
            String jsonString = JSON.toJSONString(active);
            try {
                session.getBasicRemote().sendText(jsonString);
            } catch (IOException e) {
                e.printStackTrace();
                log.error("推送异常:{}", e);
            }
        }
        }, 0, 1, TimeUnit.SECONDS);
        scheduledFutureMap.put(id, scheduledFuture);
    }

    // 当客户端断开连接时调用
    @OnClose
    public void onClose(Session session) {
        try {
            String id = session.getId();
            ScheduledFuture<?> scheduledFuture = scheduledFutureMap.get(id);
            if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
                scheduledFuture.cancel(true);
            }
            if (session != null && session.getBasicRemote() != null) {
                session.close();
            }

        } catch (IOException e) {
            log.error("关闭链接异常{}", e);
            throw new RuntimeException(e);
        }

    }
    private List<OrderActiveInfoVO> getActive(Long currentAccountId){
        FollowTraderUserEntity traderUser = followTraderUserService.getById(currentAccountId);
        List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, traderUser.getAccount()).eq(FollowTraderEntity::getPlatformId, traderUser.getPlatformId()));
        AtomicLong traderId=new AtomicLong(0);
        list.forEach(o->{
            if(o.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue())){
                traderId.set(o.getId());
            }
        });
        if(traderId.get()==0l){
            traderId.set(list.get(0).getId());
        }
        Object o1 = redisCache.get(Constant.TRADER_ACTIVE + traderId.get());
        List<OrderActiveInfoVO> orderActiveInfoList =new ArrayList<>();
        
        if (ObjectUtil.isNotEmpty(o1)){
            orderActiveInfoList = JSONObject.parseArray(o1.toString(), OrderActiveInfoVO.class);
            List<Integer> orderNos = orderActiveInfoList.stream().map(OrderActiveInfoVO::getOrderNo).toList();
            List<FollowOrderDetailEntity> orderDetails = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getAccount, traderUser.getAccount()).in(FollowOrderDetailEntity::getOrderNo, orderNos));
            Map<Integer, FollowOrderDetailEntity> map = orderDetails.stream().collect(Collectors.toMap(FollowOrderDetailEntity::getOrderNo, o -> o));
            orderActiveInfoList.forEach(o->{
                FollowOrderDetailEntity followOrderDetailEntity = map.get(o.getOrderNo());
                if(followOrderDetailEntity!=null){
                    if(followOrderDetailEntity.getType().equals(TraderCloseEnum.BUY)){
                        o.setPriceSlip(followOrderDetailEntity.getOpenPriceSlip());
                    }else{
                        o.setPriceSlip(followOrderDetailEntity.getClosePriceSlip());
                    }

                }
            });

        }
        
      return orderActiveInfoList;
    }
    // 当发生错误时调用
    @OnError
    public void onError(Session session, Throwable throwable) {
        System.out.println("Error occurred: " + throwable.getMessage());
        throwable.printStackTrace();
    }
}
