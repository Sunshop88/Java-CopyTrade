package net.maku.mascontrol.websocket;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderStatusEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.query.FollowVpsQuery;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.FollowRedisTraderVO;
import net.maku.followcom.vo.FollowVpsInfoVO;
import net.maku.followcom.vo.FollowVpsVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.PageResult;

import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.mascontrol.vo.VpsDataVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Author:  zsd
 * Date:  2024/12/6/周五 17:51
 */
@Component
@ServerEndpoint("/socket/vpsInfo/{userId}/{page}")
public class VpsInfoWebSocket {
    private Session session;
    private Integer page;
    private Long userId;
    private static Map<String, Set<Session>> sessionPool = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(VpsInfoWebSocket.class);
    private final FollowVpsService followVpsService=SpringContextUtils.getBean(FollowVpsService.class);
    private final FollowTraderService followTraderService=SpringContextUtils.getBean(FollowTraderService.class);
    private final FollowTraderSubscribeService followTraderSubscribeService=SpringContextUtils.getBean(FollowTraderSubscribeService.class);
    private final RedisCache redisCache=SpringContextUtils.getBean(RedisCache.class);
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
   private   ScheduledFuture<?> scheduledFuture;

    @OnOpen
    public void onOpen(Session session,@PathParam(value = "userId") Long userId, @PathParam(value = "page") Integer page) {
        try {
            this.session = session;
            this.page = page;
            this.userId=userId;
            Set<Session> sessionSet = sessionPool.getOrDefault(userId.toString()+page, ConcurrentHashMap.newKeySet());
            sessionSet.add(session);
            sessionPool.put(userId.toString()+page, sessionSet);
            //开启定时任务
            this.scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(() -> {
                try {
                    sendData(session, userId, page);
                } catch (Exception e) {
                    log.info("WebSocket建立连接异常" + e);
                }
            }, 0, 1, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.info("连接异常" + e);
        }
    }

    public  void  sendData(Session session,Long userId, Integer page) throws EncodeException, IOException {
        //登录账号
        FollowVpsQuery query=new FollowVpsQuery();
        query.setPage(page);
        query.setUserId(userId);
        query.setIsOpen(1);
        query.setIsOpen(1);
        query.setLimit(10);
        PageResult<FollowVpsVO> pageData = followVpsService.page(query);
        if(ObjectUtil.isNotEmpty(pageData)){
            List<Integer> ipList = pageData.getList().stream().map(FollowVpsVO::getId).toList();
            List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().in(ObjectUtil.isNotEmpty(ipList), FollowTraderEntity::getServerId, ipList));
            Map<Integer, Map<Integer, List<FollowTraderEntity>>> map = list.stream().collect(Collectors.groupingBy(FollowTraderEntity::getServerId, Collectors.groupingBy(FollowTraderEntity::getType)));
            //查询订阅关系
            Map<Long, Long> subscribeMap = followTraderSubscribeService.list().stream().collect(Collectors.toMap(FollowTraderSubscribeEntity::getSlaveId, FollowTraderSubscribeEntity::getMasterId));
            //策略数量
            pageData.getList().forEach(o -> {
                Map<Integer, List<FollowTraderEntity>> vpsMap = map.get(o.getId());
                int followNum = 0;
                int traderNum = 0;
                o.setTotal(0);
                o.setEuqit(BigDecimal.ZERO);
                o.setProfit(BigDecimal.ZERO);
                o.setLots(BigDecimal.ZERO);
                if (ObjectUtil.isNotEmpty(vpsMap)) {
                    List<FollowTraderEntity> followTraderEntities = vpsMap.get(TraderTypeEnum.SLAVE_REAL.getType());
                    List<FollowTraderEntity> masterTraderEntities = vpsMap.get(TraderTypeEnum.MASTER_REAL.getType());
                    followNum = ObjectUtil.isNotEmpty(followTraderEntities) ? followTraderEntities.size() : 0;
                    traderNum = ObjectUtil.isNotEmpty(masterTraderEntities) ? masterTraderEntities.size() : 0;
                    Stream<FollowTraderEntity> stream = ObjectUtil.isNotEmpty(followTraderEntities) ? followTraderEntities.stream() : Stream.empty();
                    System.out.println(o.getName()+followTraderEntities.size());
                    Map<Long, FollowTraderEntity> masterTrader =new HashMap<>();
                    if(ObjectUtil.isNotEmpty(masterTraderEntities)){
                        masterTrader = masterTraderEntities.stream().collect(Collectors.toMap(FollowTraderEntity::getId, Function.identity()));
                    }
                    if (ObjectUtil.isNotEmpty(stream)) {
                        Map<Long, FollowTraderEntity> finalMasterTrader = masterTrader;
                        stream.forEach(x -> {
                            //拿到masterid
                            Long masterId = subscribeMap.get(x.getId());
                            //获取master喊单者,开启了的才统计
                            FollowTraderEntity masterTraderEntity = finalMasterTrader.get(masterId);
                            if (ObjectUtil.isNotEmpty(masterTraderEntity) && masterTraderEntity.getFollowStatus()== CloseOrOpenEnum.OPEN.getValue() && masterTraderEntity.getStatus().equals(TraderStatusEnum.NORMAL.getValue())) {
                                //获取redis内的下单信息
                                if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_USER + x.getId())) && x.getStatus() == TraderStatusEnum.NORMAL.getValue() && x.getFollowStatus() == CloseOrOpenEnum.OPEN.getValue()) {
                                    FollowRedisTraderVO followRedisTraderVO = (FollowRedisTraderVO) redisCache.get(Constant.TRADER_USER + x.getId());
                                    o.setTotal(o.getTotal() + followRedisTraderVO.getTotal());
                                    o.setProfit(o.getProfit().add(ObjectUtil.isNotEmpty(followRedisTraderVO.getProfit()) ? followRedisTraderVO.getProfit() : BigDecimal.ZERO));
                                    o.setEuqit(o.getEuqit().add(followRedisTraderVO.getEuqit()));
                                    BigDecimal lots = new BigDecimal(followRedisTraderVO.getBuyNum() + "").add(new BigDecimal(followRedisTraderVO.getSellNum() + ""));
                                    o.setLots(o.getLots().add(lots));
                                }
                            }
                        });
                    }
                }
                o.setEuqit(o.getEuqit().setScale(2, BigDecimal.ROUND_HALF_UP));
                o.setProfit(o.getProfit().setScale(2, BigDecimal.ROUND_HALF_UP));
                o.setLots(o.getLots().setScale(2, BigDecimal.ROUND_HALF_UP));
                o.setFollowNum(followNum);
                o.setTraderNum(traderNum);
                System.out.println(o.getName()+o.getTotal());
            });

        }
        FollowVpsInfoVO followVpsInfo = followVpsService.getFollowVpsInfo(followTraderService,userId);
        VpsDataVo dataVo = VpsDataVo.builder().pageData(pageData).vpsInfoData(followVpsInfo).build();
        session.getBasicRemote().sendText(JSON.toJSONString(dataVo));

    }
    @OnMessage
    public void onMessage(String message, Session session) {
    }

    private void stopPeriodicTask() {
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(true);
        }
    }
    @OnClose
    public void onClose() {
        try {
            stopPeriodicTask();

            if(sessionPool.get(userId.toString()+page)!=null){
                sessionPool.get(userId.toString()+page).remove(session);
            }

        } catch (Exception e) {
            log.info("连接异常" + e);
        }
    }
}
