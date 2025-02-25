package net.maku.mascontrol.websocket;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import net.maku.followcom.service.DashboardService;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.BargainAccountVO;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.rmi.ServerException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private RedisCache redisCache = SpringContextUtils.getBean(RedisCache.class);

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
            String currentAccountId = jsonObj.getString("currentAccountId");


            ScheduledFuture<?> st = scheduledFutureMap.get(id);
            if (st != null) {
                st.cancel(true);
            }
          ScheduledFuture scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                BargainAccountVO bargainAccountVO = new BargainAccountVO();
                //选中当前账号的持仓
                if(ObjectUtil.isNotEmpty(currentAccountId)) {
                Object o1 = redisCache.get(Constant.TRADER_ACTIVE + currentAccountId);
                List<OrderActiveInfoVO> orderActiveInfoList =new ArrayList<>();
                if (ObjectUtil.isNotEmpty(o1)){
                    orderActiveInfoList = JSONObject.parseArray(o1.toString(), OrderActiveInfoVO.class);

                }
                bargainAccountVO.setOrderActiveInfoList(orderActiveInfoList);
                }
                ObjectMapper objectMapper = new ObjectMapper();
                JavaTimeModule javaTimeModule = new JavaTimeModule();
                //格式化时间格式
                javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                objectMapper.registerModule(javaTimeModule);
                String s = objectMapper.writeValueAsString(bargainAccountVO);
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
