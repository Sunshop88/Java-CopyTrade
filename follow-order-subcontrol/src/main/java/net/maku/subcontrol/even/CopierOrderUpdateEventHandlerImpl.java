package net.maku.subcontrol.even;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowOrderDetailEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.enums.TraderRepairOrderEnum;
import net.maku.followcom.service.FollowOrderDetailService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.OrderActiveInfoVO;
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
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.UpdateAction;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
            FollowTraderEntity master = followTraderService.getFollowById(subscribeEntity.getMasterId());
            List<FollowOrderDetailEntity> list = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getAccount, follow.getAccount()).eq(FollowOrderDetailEntity::getOrderNo, order.Ticket).eq(FollowOrderDetailEntity::getPlatform, follow.getPlatform()));
            switch (orderUpdateEventArgs.Action) {
                case PositionOpen:
                case PendingFill:
                    ThreadPoolUtils.getExecutor().execute(()->{
                        try {
                            Thread.sleep(4000);
                        } catch (Exception e) {

                        }
                        String key1 = Constant.REPAIR_SEND + "：" + follow.getAccount();
                        boolean lock1 = redissonLockUtil.lock(key1, 500, -1, TimeUnit.SECONDS);
                        try {
                            Thread.sleep(1000);
                        } catch (Exception e) {

                        }
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
                    });
                    break;
                case PositionClose:
                    ThreadPoolUtils.getExecutor().execute(()->{
                        try {
                            Thread.sleep(2000);
                        } catch (Exception e) {

                        }
                        String key = Constant.REPAIR_CLOSE + "：" + follow.getAccount();
                        boolean lock = redissonLockUtil.lock(key, 500, -1, TimeUnit.SECONDS);
                        try {
                            try {
                                Thread.sleep(1000);
                            } catch (Exception e) {

                            }
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
                                log.info("监听跟单漏平删除,key:{},key:{},订单号:{},val:{}", Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(), follow.getAccount(),magical, JSONObject.toJSONString(repairInfoVOS));
                            }
                        }finally {
                            redissonLockUtil.unlock(key);
                        }
                        log.info("监听跟单漏平删除:跟单账号{},订单号：{},平台:{}", follow.getAccount(),order.Ticket,follow.getPlatform());

                    });
                    repairSend(follow,master,copier4ApiTrader.quoteClient);
                    break;
                default:
                    log.error("Unexpected value: " + orderUpdateEventArgs.Action);
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public void repairSend(FollowTraderEntity follow, FollowTraderEntity master, QuoteClient quoteClient){
        String openKey = Constant.REPAIR_SEND + "：" + follow.getAccount();
        boolean lock = redissonLockUtil.lock(openKey, 30, -1, TimeUnit.SECONDS);
        try {
            if(lock) {
                //如果主账号这边都平掉了,就删掉这笔订单
                Object o1 = redisUtil.get(Constant.TRADER_ACTIVE + master.getId());
                List<OrderActiveInfoVO> orderActiveInfoList = new ArrayList<>();
                if (ObjectUtil.isNotEmpty(o1)) {
                    orderActiveInfoList = JSONObject.parseArray(o1.toString(), OrderActiveInfoVO.class);
                }
                Map<Integer, OrderRepairInfoVO> repairInfoVOS = new HashMap<Integer, OrderRepairInfoVO>();
                if(orderActiveInfoList!=null && orderActiveInfoList.size()>0) {
                    orderActiveInfoList.stream().forEach(orderInfo -> {
                        AtomicBoolean existsInActive = new AtomicBoolean(true);
                        if (quoteClient != null) {
                            existsInActive.set(Arrays.stream(quoteClient.GetOpenedOrders()).anyMatch(order -> String.valueOf(orderInfo.getOrderNo()).equalsIgnoreCase(order.MagicNumber + "")));
                        } else {
                            Object o2 = redisUtil.get(Constant.TRADER_ACTIVE + follow.getId());
                            List<OrderActiveInfoVO> followActiveInfoList = new ArrayList<>();
                            if (ObjectUtil.isNotEmpty(o2)) {
                                followActiveInfoList = JSONObject.parseArray(o2.toString(), OrderActiveInfoVO.class);
                            }
                            existsInActive.set(followActiveInfoList.stream().anyMatch(order -> String.valueOf(orderInfo.getOrderNo()).equalsIgnoreCase(order.getMagicNumber().toString())));
                        }
                        if (!existsInActive.get()) {
                            OrderRepairInfoVO orderRepairInfoVO = new OrderRepairInfoVO();
                            orderRepairInfoVO.setRepairType(TraderRepairOrderEnum.SEND.getType());
                            orderRepairInfoVO.setMasterLots(orderInfo.getLots());
                            orderRepairInfoVO.setMasterOpenTime(orderInfo.getOpenTime());
                            orderRepairInfoVO.setMasterProfit(orderInfo.getProfit());
                            orderRepairInfoVO.setMasterSymbol(orderInfo.getSymbol());
                            orderRepairInfoVO.setMasterTicket(orderInfo.getOrderNo());
                            orderRepairInfoVO.setMasterOpenPrice(orderInfo.getOpenPrice());
                            orderRepairInfoVO.setMasterType(orderInfo.getType());
                            orderRepairInfoVO.setMasterId(master.getId());
                            orderRepairInfoVO.setSlaveAccount(follow.getAccount());
                            orderRepairInfoVO.setSlaveType(orderInfo.getType());
                            orderRepairInfoVO.setSlavePlatform(follow.getPlatform());
                            orderRepairInfoVO.setSlaveId(follow.getId());
                            repairInfoVOS.put(orderInfo.getOrderNo(), orderRepairInfoVO);
                        }
                        redisUtil.hSetStr(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), follow.getAccount().toString(), JSON.toJSONString(repairInfoVOS));
                        log.info("漏开补偿数据写入,key:{},key:{},订单号:{},val:{},", Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), follow.getAccount().toString(), orderInfo.getOrderNo(), JSONObject.toJSONString(repairInfoVOS));
                    });
                }else{
                    redisUtil.hDel(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), follow.getAccount().toString());
                }
            }
        } catch (Exception e) {
            log.error("漏单检查写入异常"+e);
        }finally {
            redissonLockUtil.unlock(openKey);
        }
    }

}
