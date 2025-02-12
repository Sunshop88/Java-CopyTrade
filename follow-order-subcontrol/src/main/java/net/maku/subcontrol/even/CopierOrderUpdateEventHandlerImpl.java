package net.maku.subcontrol.even;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowOrderDetailEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.service.FollowOrderDetailService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.OrderRepairInfoVO;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.config.JacksonConfig;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.service.FollowOrderHistoryService;
import net.maku.subcontrol.service.impl.FollowOrderHistoryServiceImpl;
import net.maku.subcontrol.trader.AbstractApiTrader;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.OrderUpdateEventArgs;
import online.mtapi.mt4.UpdateAction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Samson Bruce
 */
@Slf4j
public class CopierOrderUpdateEventHandlerImpl extends OrderUpdateHandler {
    AbstractApiTrader copier4ApiTrader;
    protected FollowOrderHistoryService followOrderHistoryService;
    private RedisUtil redisUtil = SpringContextUtils.getBean(RedisUtil.class);
    private FollowTraderService followTraderService= SpringContextUtils.getBean(FollowTraderService.class);
    private FollowTraderSubscribeService subscribeService= SpringContextUtils.getBean(FollowTraderSubscribeService.class);
    private FollowOrderDetailService followOrderDetailService= SpringContextUtils.getBean(FollowOrderDetailService.class);
    // 设定时间间隔，单位为毫秒
    private final long interval = 1000; // 1秒间隔
    private net.maku.framework.common.config.JacksonConfig JacksonConfig;

    public CopierOrderUpdateEventHandlerImpl(AbstractApiTrader abstract4ApiTrader) {
        super();
        this.leader = abstract4ApiTrader.getTrader();
        this.copier4ApiTrader = abstract4ApiTrader;
        this.followOrderHistoryService = SpringContextUtils.getBean(FollowOrderHistoryServiceImpl.class);
    }

    @Override
    public void invoke(Object sender, OrderUpdateEventArgs orderUpdateEventArgs) {
        try {
            //发送websocket消息标识
            if (Objects.requireNonNull(orderUpdateEventArgs.Action) == UpdateAction.PositionClose) {
                Order order = orderUpdateEventArgs.Order;
                FollowTraderEntity follow = copier4ApiTrader.getTrader();
                FollowTraderSubscribeEntity subscribeEntity = subscribeService.getOne(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getSlaveId, follow.getId()));
                FollowTraderEntity master = followTraderService.getById(subscribeEntity.getMasterId());
                List<FollowOrderDetailEntity> list = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getAccount, master.getAccount()).eq(FollowOrderDetailEntity::getOrderNo, order.Ticket).eq(FollowOrderDetailEntity::getPlatform, follow.getPlatform()));
                if(list!=null && list.size()>0){
                    Integer magical = list.get(0).getMagical();
                    redisUtil.hDel(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST +"#"+follow.getPlatform()+"#"+master.getPlatform()+ "#" + follow.getAccount() + "#" + master.getAccount(), magical.toString());
                    //删除漏单redis记录
                    Object o1 = redisUtil.hGetStr(Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(), follow.getAccount());
                    Map<Integer, OrderRepairInfoVO> repairInfoVOS = new HashMap();
                    if (o1!=null && o1.toString().trim().length()>0){
                        repairInfoVOS= JSONObject.parseObject(o1.toString(), Map.class);
                    }
                    repairInfoVOS.remove(magical);
                    if(repairInfoVOS==null || repairInfoVOS.size()==0){
                        redisUtil.del(Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId());
                    }else{
                        redisUtil.hSetStr(Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(), follow.getAccount(),JSONObject.toJSONString(repairInfoVOS));
                    }
                    log.info("监听跟单漏平删除,key:{},key:{},val:{},订单号:{}",Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(), follow.getAccount().toString(),JSONObject.toJSONString(repairInfoVOS),magical);
                }
                log.info("跟单发送平仓mq" + leader.getId());
                ThreadPoolUtils.getExecutor().execute(()-> {
                    //发送平仓MQ
                    ObjectMapper mapper = JacksonConfig.getObjectMapper();
                    try {
                        producer.sendMessage(mapper.writeValueAsString(getMessagePayload(order)));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }


}
