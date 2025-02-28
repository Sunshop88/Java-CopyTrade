package net.maku.subcontrol.websocket;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import net.maku.followcom.entity.FollowSysmbolSpecificationEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.query.FollowTraderQuery;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.followcom.service.impl.FollowTraderSubscribeServiceImpl;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.FollowRedisTraderVO;
import net.maku.followcom.vo.FollowTraderVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import online.mtapi.mt4.PlacedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 账号信息
 */
@Component
@ServerEndpoint("/socket/trader/slave/{page}/{limit}/{traderId}") //此注解相当于设置访问URL
public class TraderAccountSlaveWebSocket {

    private static final Logger log = LoggerFactory.getLogger(TraderAccountSlaveWebSocket.class);
    private String page;
    private Session session;

    private String limit;
    private String traderId;

    private static Map<String, Set<Session>> sessionPool = new ConcurrentHashMap<>();

    private FollowTraderServiceImpl followTraderService= SpringContextUtils.getBean( FollowTraderServiceImpl.class);
    private RedisCache redisCache= SpringContextUtils.getBean( RedisCache.class);
    private List<FollowTraderVO> listFollow=new ArrayList<>();
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private Future<?> scheduledTask;
    private FollowTraderSubscribeService followTraderSubscribeService=SpringContextUtils.getBean(FollowTraderSubscribeServiceImpl.class);
    @OnOpen
    public void onOpen(Session session, @PathParam(value = "page") String page, @PathParam(value = "limit") String limit, @PathParam(value = "traderId") String traderId) {
        try {
            this.session = session;
            this.page = page;
            this.limit = limit;
            this.traderId=traderId;
            Set<Session> sessionSet = sessionPool.getOrDefault(page + limit+traderId, ConcurrentHashMap.newKeySet());
            sessionSet.add(session);
            sessionPool.put(page + limit+traderId, sessionSet);
            FollowTraderQuery followTraderQuer=new FollowTraderQuery();
            followTraderQuer.setPage(Integer.valueOf(page));
            followTraderQuer.setLimit(Integer.valueOf(limit));
            followTraderQuer.setServerIp(FollowConstant.LOCAL_HOST);
            List<FollowTraderSubscribeEntity> list = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterId, Integer.valueOf(traderId)));
            List<Long> collect = list.stream().map(FollowTraderSubscribeEntity::getSlaveId).toList();
            if(ObjectUtil.isEmpty(collect)) {
                return;
            }
            followTraderQuer.setTraderList(collect);
            PageResult<FollowTraderVO> pageResult = followTraderService.page(followTraderQuer);
            if (ObjectUtil.isNotEmpty(pageResult)&&ObjectUtil.isNotEmpty(pageResult.getList())){
                listFollow=pageResult.getList();
                startPeriodicTask();
            }
        } catch (Exception e) {
            log.info("连接异常"+e);
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    private void startPeriodicTask() {
        // 每秒钟发送一次消息
        List<Long> list = listFollow.stream().map(o -> o.getId()).toList();
        Map<Long, FollowTraderEntity> collect = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().in(FollowTraderEntity::getId, list)).stream().collect(Collectors.toMap(FollowTraderEntity::getId, i -> i));
        Map<Long, FollowTraderSubscribeEntity> collect1 = followTraderSubscribeService.getSubscribeOrder(Long.valueOf(traderId)).stream().collect(Collectors.toMap(FollowTraderSubscribeEntity::getSlaveId, i -> i));
        scheduledTask = scheduledExecutorService.scheduleAtFixedRate(() -> sendPeriodicMessage(page, limit,traderId,collect,collect1), 0, 2, TimeUnit.SECONDS);
    }

    private void stopPeriodicTask() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(true);
        }
    }


    private void sendPeriodicMessage(String page , String limit, String traderId, Map<Long, FollowTraderEntity> traderEntityMap,Map<Long, FollowTraderSubscribeEntity>  followTraderSubscribeEntity) {
        //查询用户数据
        List<FollowRedisTraderVO> followRedisTraderVOS=new ArrayList<>();
        listFollow.forEach(o->{
            FollowTraderEntity trader = traderEntityMap.get(o.getId());
            FollowTraderSubscribeEntity followTraderSubscribe = followTraderSubscribeEntity.get(o.getId());
            FollowRedisTraderVO followRedisTraderVO =new FollowRedisTraderVO();
            if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_USER + o.getId()))) {
                followRedisTraderVO = (FollowRedisTraderVO) redisCache.get(Constant.TRADER_USER + o.getId());
                if (ObjectUtil.isEmpty(followRedisTraderVO.getAccount())){
                    followRedisTraderVO.setFollowMode(getfollowMode(followTraderSubscribe));
                    followRedisTraderVO.setFollowStatus(followTraderSubscribe.getFollowStatus());
                    followRedisTraderVO.setAccount(trader.getAccount());
                    followRedisTraderVO.setConnectionStatus(trader.getStatus());
                    followRedisTraderVO.setPlacedType(ObjectUtil.isNotEmpty( followTraderSubscribe.getPlacedType())? PlacedType.forValue(followTraderSubscribe.getPlacedType()).name():"Client");
                    followRedisTraderVO.setRemark(trader.getRemark());
                    followRedisTraderVO.setPlatform(trader.getPlatform());
                    followRedisTraderVO.setLeverage(trader.getLeverage());
                }
                List<FollowTraderSubscribeEntity> subscribeOrder = followTraderSubscribeService.getSubscribeOrder(followRedisTraderVO.getTraderId());
                followRedisTraderVO.setSlaveNum(subscribeOrder.size());
            }
            followRedisTraderVOS.add(followRedisTraderVO);
        });
        pushMessage(page,limit,traderId,JsonUtils.toJsonString(followRedisTraderVOS));
    }


    @OnClose
    public void onClose() {
        try {
            sessionPool.get(page + limit+traderId).remove(session);
            stopPeriodicTask(); // 关闭时停止定时任务
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 服务器端推送消息
     */
    public void pushMessage(String page, String limit,String traderId,String message) {
        try {
            Set<Session> sessionSet = sessionPool.get(page + limit+traderId);
            if (ObjectUtil.isEmpty(sessionSet)) {
                return;
            }
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

    public Boolean isConnection(String page, String limit,String traderId) {
        return sessionPool.containsKey(page + limit+traderId);
    }

    private String getfollowMode(FollowTraderSubscribeEntity followSub) {
        String direction = followSub.getFollowDirection() == 0 ? "正" : "反";
        //  0-固定手数 1-手数比例 2-净值比例
        String mode =null;
        switch (followSub.getFollowMode()) {
            case(0):
                mode="固定";
                break;
            case(1):
                mode="手";
                break;
            case(2):
                mode="净";
                break;
        }
        return direction+"|"+mode+"*"+followSub.getFollowParam();
    }
}
