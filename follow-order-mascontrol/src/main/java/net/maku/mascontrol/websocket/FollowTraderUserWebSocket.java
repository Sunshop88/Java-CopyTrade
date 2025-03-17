package net.maku.mascontrol.websocket;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.google.common.util.concurrent.AtomicDouble;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.query.FollowTraderUserQuery;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderUserService;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.AtomicBigDecimal;
import net.maku.followcom.vo.BargainAccountVO;
import net.maku.followcom.vo.FollowTraderUserVO;
import net.maku.followcom.vo.TraderUserStatVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.ThreadPoolUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.rmi.ServerException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

import static dm.jdbc.util.DriverUtil.log;

/**
 * Author:  zsd
 * Date:  2025/3/17/周一 16:29
 */
@Component
@ServerEndpoint("/socket/followTraderUser")
public class FollowTraderUserWebSocket {
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private Map<String, ScheduledFuture<?>> scheduledFutureMap = new HashMap<>();
    private final FollowTraderUserService followTraderUserService=SpringContextUtils.getBean(FollowTraderUserService.class);
    @OnOpen
    public void onOpen(Session session) throws IOException {
    }
    // 当接收到客户端的消息时调用
    @OnMessage
    public void onMessage(String message, Session session) throws ServerException {
        try {
            String id = session.getId();
            JSONObject jsonObj = JSONObject.parseObject(message);

            String traderUserJson = jsonObj.getString("traderUserQuery");

            FollowTraderUserQuery traderUserQuery = JSONObject.parseObject(traderUserJson, FollowTraderUserQuery.class);

            ScheduledFuture<?> st = scheduledFutureMap.get(id);
            if (st != null) {
                st.cancel(true);
            }
            ScheduledFuture scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(() -> {
                try {
                    TraderUserStatVO traderUserStatVO = followTraderUserService.searchPage(traderUserQuery);
                    ObjectMapper objectMapper = new ObjectMapper();
                    JavaTimeModule javaTimeModule = new JavaTimeModule();
                    //格式化时间格式
                    javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    objectMapper.registerModule(javaTimeModule);
                    String s = objectMapper.writeValueAsString(traderUserStatVO);
                    session.getBasicRemote().sendText(s);
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
