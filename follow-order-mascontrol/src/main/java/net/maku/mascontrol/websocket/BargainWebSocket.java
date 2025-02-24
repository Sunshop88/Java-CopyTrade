package net.maku.mascontrol.websocket;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import net.maku.followcom.service.DashboardService;
import net.maku.followcom.util.SpringContextUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.rmi.ServerException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static dm.jdbc.util.DriverUtil.log;

/**
 * Author:  zsd
 * Date:  2025/2/24/周一 16:46
 */
@Component
@ServerEndpoint("/socket/bargain")
public class BargainWebSocket {
    private final DashboardService dashboardService = SpringContextUtils.getBean(DashboardService.class);
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private Map<String, ScheduledFuture<?>> scheduledFutureMap = new HashMap<>();
    @OnOpen
    public void onOpen(Session session) throws IOException {
    }
    // 当接收到客户端的消息时调用
    @OnMessage
    public void onMessage(String message, Session session) throws ServerException {
        try {
            String id = session.getId();
            JSONObject jsonObj = JSONObject.parseObject(message);
            JSONArray accountIds = jsonObj.getJSONArray("accountIds");
             ScheduledFuture<?> st = scheduledFutureMap.get(id);
            if (st != null) {
                st.cancel(true);
            }
          ScheduledFuture scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                JSONObject json = new JSONObject();
                session.getBasicRemote().sendText(json.toJSONString());
            } catch (Exception e) {
                e.printStackTrace();
                log.error("推送异常:{}", e);

            }
        }, 0, 1, TimeUnit.SECONDS);
        scheduledFutureMap.put(id, scheduledFuture);
    } catch (Exception e) {
        log.error(e.getMessage());
        throw new ServerException(e.getMessage());

    }
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

    // 当发生错误时调用
    @OnError
    public void onError(Session session, Throwable throwable) {
        System.out.println("Error occurred: " + throwable.getMessage());
        throwable.printStackTrace();
    }

}
