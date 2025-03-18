package net.maku.mascontrol.websocket;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import net.maku.api.module.system.UserApi;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.TradeErrorCodeEnum;
import net.maku.followcom.service.*;
import net.maku.followcom.service.impl.*;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.mascontrol.vo.FollowBaiginInstructSubVO;
import net.maku.mascontrol.vo.FollowBaiginInstructVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * 下单指令
 */
@Component
@ServerEndpoint("/socket/bargain/instruct") //此注解相当于设置访问URL
public class BarginInstructWebSocket {

    private static final Logger log = LoggerFactory.getLogger(BarginInstructWebSocket.class);
    private Session session;
    private FollowTraderServiceImpl followTraderService = SpringContextUtils.getBean(FollowTraderServiceImpl.class);
    private static Map<String, Set<Session>> sessionPool = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(0, Thread.ofVirtual().factory());
    private FollowOrderInstructService followOrderInstructService = SpringContextUtils.getBean(FollowOrderInstructServiceImpl.class);
    private FollowOrderDetailService followOrderDetailService = SpringContextUtils.getBean(FollowOrderDetailServiceImpl.class);
    private UserApi userApi = SpringContextUtils.getBean(UserApi.class);
    private static Map<String, Future<?>> scheduledTasks = new ConcurrentHashMap<>(); // 用于存储每个连接的定时任务

    @OnOpen
    public void onOpen(Session session) {
    }

    private void startPeriodicTask(String id,Session session) {
        // 每秒钟发送一次消息
        Future<?> scheduledTask = scheduledExecutorService.scheduleAtFixedRate(() -> sendPeriodicMessage( id,session), 0, 2, TimeUnit.SECONDS);
        scheduledTasks.put(session.getId(), scheduledTask); // 将任务保存到任务映射中
    }

    private void sendPeriodicMessage(String id,Session session) {
        FollowBaiginInstructVO followBaiginInstructVO = new FollowBaiginInstructVO();
        FollowOrderInstructEntity followOrderInstruct = null;
        // 获取最新指令
        Optional<FollowOrderInstructEntity> followOrderInstruct1 = followOrderInstructService.list(new LambdaQueryWrapper<FollowOrderInstructEntity>().eq(FollowOrderInstructEntity::getCreator,id).orderByDesc(FollowOrderInstructEntity::getCreateTime)).stream().findFirst();
        if (followOrderInstruct1.isPresent()) {
            followOrderInstruct = followOrderInstruct1.get();
        }
        if (ObjectUtil.isNotEmpty(followOrderInstruct)) {
            BeanUtil.copyProperties(followOrderInstruct, followBaiginInstructVO);
            followBaiginInstructVO.setCreator(userApi.getUserById(followBaiginInstructVO.getCreator()).getUsername());
            List<FollowOrderDetailEntity> list = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getSendNo,followOrderInstruct.getOrderNo()).orderByDesc(FollowOrderDetailEntity::getId));
            List<FollowBaiginInstructSubVO> followBaiginInstructSubVOList = new ArrayList<>();
            list.forEach(o -> {
                FollowBaiginInstructSubVO followBaiginInstructSubVO = new FollowBaiginInstructSubVO();
                BeanUtil.copyProperties(o, followBaiginInstructSubVO);
                FollowTraderEntity followTraderEntity = followTraderService.getFollowById(o.getTraderId());
                followBaiginInstructSubVO.setPlatform(followTraderEntity.getPlatform());
                if (ObjectUtil.isEmpty(o.getCloseTime()) && ObjectUtil.isNotEmpty(o.getRemark())) {
                    // 失败原因
                    TradeErrorCodeEnum description = TradeErrorCodeEnum.getDescription(o.getRemark());
                    if (ObjectUtil.isEmpty(description)) {
                        followBaiginInstructSubVO.setStatusComment("其他原因");
                    } else {
                        followBaiginInstructSubVO.setStatusComment(description.getDescription());
                    }
                } else {
                    followBaiginInstructSubVO.setCreateTime(o.getRequestOpenTime());
                    followBaiginInstructSubVO.setStatusComment("成功");
                }
                followBaiginInstructSubVOList.add(followBaiginInstructSubVO);
            });
            followBaiginInstructVO.setFollowBaiginInstructSubVOList(followBaiginInstructSubVOList);
        }
        pushMessage(session,JsonUtils.toJsonString(followBaiginInstructVO));
    }

    @OnClose
    public void onClose(Session session) {
        try {
            String id = session.getId();
            Future<?> future = scheduledTasks.get(id);
            if (future != null && !future.isCancelled()) {
                future.cancel(true);
            }
            if (session != null && session.getBasicRemote() != null) {
                session.close();
            }

        } catch (IOException e) {
            log.error("关闭链接异常{}", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 服务器端推送消息
     */
    public void pushMessage(Session session,String message) {
        try {
            if (session != null && session.getBasicRemote() != null) {
                synchronized (session) {
                    session.getBasicRemote().sendText(message);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnMessage
    public void onMessage(Session session,String message) {
        JSONObject jsonObj = JSONObject.parseObject(message);
        String userId = jsonObj.getString("id");
        //多账号获取数据
        String id = session.getId();
        Future<?> st = scheduledTasks.get(id);
        if (st != null) {
            st.cancel(true);
            scheduledTasks.clear();
        }
        if (!scheduledTasks.containsKey(id)) {
            startPeriodicTask(userId,session); // 启动定时任务
        }
    }

    public Boolean isConnection(String id) {
        return sessionPool.containsKey(id);
    }
}
