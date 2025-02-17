package net.maku.subcontrol.even;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import net.maku.framework.common.cache.RedissonLockUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
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
import java.util.concurrent.TimeUnit;

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
    private final RedissonLockUtil redissonLockUtil=SpringContextUtils.getBean(RedissonLockUtil.class);;
    // 设定时间间隔，单位为毫秒
    private final long interval = 1000; // 1秒间隔

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
//            if (Objects.requireNonNull(orderUpdateEventArgs.Action) == UpdateAction.PositionClose) {
//                Order x = orderUpdateEventArgs.Order;
//                log.info("跟单发送平仓mq" + leader.getId());
//                ThreadPoolUtils.getExecutor().execute(()-> {
//                    //发送平仓MQ
//                    producer.sendMessage(JSONUtil.toJsonStr(getMessagePayload(x)));
//                });
//            }
            Order order = orderUpdateEventArgs.Order;
            FollowTraderEntity follow = copier4ApiTrader.getTrader();
            FollowTraderSubscribeEntity subscribeEntity = subscribeService.getOne(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getSlaveId, follow.getId()));
            FollowTraderEntity master = followTraderService.getById(subscribeEntity.getMasterId());
            List<FollowOrderDetailEntity> list = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getAccount, follow.getAccount()).eq(FollowOrderDetailEntity::getOrderNo, order.Ticket).eq(FollowOrderDetailEntity::getPlatform, follow.getPlatform()));
            switch (orderUpdateEventArgs.Action) {
                case PositionOpen:
                case PendingFill:
                    try {
                        Thread.sleep(3000);
                    } catch (Exception e) {

                    }
                    String key1 = Constant.REPAIR_SEND + "：" + follow.getAccount();
                    boolean lock1 = redissonLockUtil.lock(key1, 10, -1, TimeUnit.SECONDS);
                    log.info("监听跟单漏开删除开始:跟单账号:{},跟单订单号：{}", follow.getAccount(),order.Ticket);
                    try {
                        if (lock1) {
                            Integer mg = order.MagicNumber;
                            Object o2 = redisUtil.hGetStr(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), follow.getAccount().toString());
                            Map<Integer, OrderRepairInfoVO> repairVOS = new HashMap();
                            if (o2 != null && o2.toString().trim().length() > 0) {
                                repairVOS = JSONObject.parseObject(o2.toString(), Map.class);
                            }
                            repairVOS.remove(mg);
                            if (repairVOS == null || repairVOS.size() == 0) {
                                redisUtil.hDel(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), follow.getAccount().toString());
                            } else {
                                redisUtil.hSetStr(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), follow.getAccount().toString(), JSONObject.toJSONString(repairVOS));
                            }
                            log.info("监听跟单漏单删除,key:{},key:{},喊单订单号:{},val:{}", Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), follow.getAccount(), mg, JSONObject.toJSONString(repairVOS));
                        }
                    }finally {
                            redissonLockUtil.unlock(key1);
                        }
                    log.info("监听跟单漏开删除:跟单账号:{},订单号：{},平台:{},跟单订单号：{}", follow.getAccount(),order.Ticket,follow.getPlatform(),list);
                    break;
                case PositionClose:
                    try {
                        Thread.sleep(3000);
                    } catch (Exception e) {

                    }
                    String key = Constant.REPAIR_CLOSE + "：" + follow.getAccount();
                    boolean lock = redissonLockUtil.lock(key, 10, -1, TimeUnit.SECONDS);
                    try {
                        if(lock) {
                                Integer magical = order.MagicNumber;
                                redisUtil.hDel(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST + "#" + follow.getPlatform() + "#" + master.getPlatform() + "#" + follow.getAccount() + "#" + master.getAccount(), magical.toString());
                                //删除漏单redis记录
                                Object o1 = redisUtil.hGetStr(Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(), follow.getAccount().toString());
                                Map<Integer, OrderRepairInfoVO> repairInfoVOS = new HashMap();
                                if (o1 != null && o1.toString().trim().length() > 0) {
                                    repairInfoVOS = JSONObject.parseObject(o1.toString(), Map.class);
                                }
                                repairInfoVOS.remove(magical);
                                if (repairInfoVOS == null || repairInfoVOS.size() == 0) {
                                    redisUtil.hDel(Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(),follow.getAccount().toString());
                                } else {
                                    redisUtil.hSetStr(Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(), follow.getAccount().toString(), JSONObject.toJSONString(repairInfoVOS));
                                }
                                log.info("监听跟单漏平删除,key:{},key:{},val:{},订单号:{}", Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(), follow.getAccount(), JSONObject.toJSONString(repairInfoVOS), magical);
                        }
                        }finally {
                            redissonLockUtil.unlock(key);
                        }
                    log.info("监听跟单漏平删除:订单{},跟单账号{},订单号：{},平台:{}",list, follow.getAccount(),order.Ticket,follow.getPlatform());
                    break;
                default:
                    log.error("Unexpected value: " + orderUpdateEventArgs.Action);
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }


}
