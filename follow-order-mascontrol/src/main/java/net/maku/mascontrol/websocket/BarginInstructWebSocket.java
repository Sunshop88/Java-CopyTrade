package net.maku.mascontrol.websocket;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.FollowMasOrderStatusEnum;
import net.maku.followcom.enums.TradeErrorCodeEnum;
import net.maku.followcom.service.*;
import net.maku.followcom.service.impl.*;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.mascontrol.vo.FollowBaiginInstructSubVO;
import net.maku.mascontrol.vo.FollowBaiginInstructVO;
import net.maku.mascontrol.vo.FollowBarginOrderVO;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
    private FollowTraderServiceImpl followTraderService= SpringContextUtils.getBean( FollowTraderServiceImpl.class);
    private static Map<String, Set<Session>> sessionPool = new ConcurrentHashMap<>();
    private RedisUtil redisUtil=SpringContextUtils.getBean(RedisUtil.class);;
    private ScheduledFuture<?> scheduledFuture;
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private FollowOrderInstructService followOrderInstructService= SpringContextUtils.getBean( FollowOrderInstructServiceImpl.class);
    private FollowOrderDetailService followOrderDetailService= SpringContextUtils.getBean( FollowOrderDetailServiceImpl.class);

    @OnOpen
    public void onOpen(Session session) {
        try {
            if (scheduledExecutorService.isTerminated()){
                scheduledExecutorService.shutdownNow();
            }
            this.session = session;
            startPeriodicTask();
        } catch (Exception e) {
            log.info("连接异常"+e);
            throw new RuntimeException();
        }
    }


    private void startPeriodicTask() {
        // 每秒钟发送一次消息
        scheduledExecutorService.scheduleAtFixedRate(() -> sendPeriodicMessage(), 0, 1, TimeUnit.SECONDS);
    }

    private void stopPeriodicTask() {
        scheduledExecutorService.shutdown();
    }

    private void sendPeriodicMessage(){
        FollowBaiginInstructVO followBaiginInstructVO = new FollowBaiginInstructVO();
        FollowOrderInstructEntity followOrderInstruct = null;
        //获取最新指令
        Optional<FollowOrderInstructEntity> followOrderInstruct1 = followOrderInstructService.list(new LambdaQueryWrapper<FollowOrderInstructEntity>().orderByDesc(FollowOrderInstructEntity::getCreateTime)).stream().findFirst();
        if (followOrderInstruct1.isPresent()) {
            followOrderInstruct = followOrderInstruct1.get();
        }
        if (ObjectUtil.isNotEmpty(followOrderInstruct)){
            BeanUtil.copyProperties(followOrderInstruct,followBaiginInstructVO);
            List<FollowOrderDetailEntity> list = followOrderDetailService.getInstruct(followOrderInstruct.getOrderNo());
            List<FollowBaiginInstructSubVO> followBaiginInstructSubVOList=new ArrayList<>();
            list.forEach(o->{
                FollowBaiginInstructSubVO followBaiginInstructSubVO=new FollowBaiginInstructSubVO();
                BeanUtil.copyProperties(o,followBaiginInstructSubVO);
                if (ObjectUtil.isNotEmpty(o.getRemark())){
                    //失败原因
                    TradeErrorCodeEnum description = TradeErrorCodeEnum.getDescription(o.getRemark());
                    if (ObjectUtil.isNotEmpty(description)){
                        followBaiginInstructSubVO.setStatusComment("其他原因");
                    }else {
                        followBaiginInstructSubVO.setStatusComment(description.getDescription());
                    }
                }else {
                    followBaiginInstructSubVO.setStatusComment("成功");
                }
                followBaiginInstructSubVOList.add(followBaiginInstructSubVO);
            });
            followBaiginInstructVO.setFollowBaiginInstructSubVOList(followBaiginInstructSubVOList);
        }
        pushMessage(JsonUtils.toJsonString(followBaiginInstructVO));
    }



    @OnClose
    public void onClose() {
        try {
            if (session != null && session.isOpen()) {
                if (scheduledExecutorService.isTerminated()){
                    scheduledExecutorService.isShutdown();
                }
                session.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 服务器端推送消息
     */
    public void pushMessage(String message) {
        try {
            if (session != null && session.isOpen()) {
                synchronized (session) {
                    session.getBasicRemote().sendText(message);
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
