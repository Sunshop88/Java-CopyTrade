package net.maku.subcontrol.websocket;

/**
 * Author:  zsd
 * Date:  2024/12/6/周五 10:01
 */

import com.alibaba.fastjson.JSON;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.util.SpringContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import jakarta.websocket.OnMessage;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
@Component
@ServerEndpoint("/socket/vpsData/{vpsId}/{traderId}")
public class VpsDataWebSocket {

    private Session session;
    private static Map<String, Set<Session>> sessionPool = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(VpsDataWebSocket.class);
    private Integer vpsId;
    private Long traderId;

    private FollowVpsService followVpsService= SpringContextUtils.getBean(FollowVpsService.class);

    private FollowTraderService followTraderService= SpringContextUtils.getBean(FollowTraderService.class);
    public static ScheduledThreadPoolExecutor scheduledThreadPoolExecutor=new ScheduledThreadPoolExecutor(10);

    @OnOpen
    public void onOpen(Session session, @PathParam(value = "vpsId") Integer vpsId, @PathParam(value = "traderId") Long traderId) {
        try {
            this.session = session;
            this.vpsId = vpsId;
            this.traderId = traderId;
            Set<Session> sessionSet = sessionPool.getOrDefault(traderId + traderId, ConcurrentHashMap.newKeySet());
            sessionSet.add(session);
            sessionPool.put(vpsId+traderId+"", sessionSet);
            //开启定时任务
            scheduledThreadPoolExecutor.scheduleAtFixedRate(()->{
                try {
                    sendData(session,vpsId,traderId);
                } catch (IOException e) {
                    log.info("WebSocket建立连接异常" + e);
                }
            },0,1, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.info("连接异常" + e);
        }
    }

    public  void  sendData(Session session,Integer vpsId, Long traderId) throws IOException {
         List<List<BigDecimal>> statByVpsId = followVpsService.getStatByVpsId(vpsId, traderId, followTraderService);
         session.getBasicRemote().sendText(JSON.toJSONString(statByVpsId.toString()));


    }
    @OnMessage
    public void onMessage(String message, Session session) {
    }


    @OnClose
    public void onClose() {
        try {
            sessionPool.get(vpsId+traderId).remove(session);
        } catch (Exception e) {
            log.info("连接异常" + e);
        }
    }
}
