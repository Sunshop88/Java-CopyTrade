package net.maku.subcontrol.trader.strategy;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.*;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.vo.OrderRepairInfoVO;
import net.maku.framework.common.cache.RedissonLockUtil;
import net.maku.framework.common.config.JacksonConfig;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.security.user.SecurityUser;
import net.maku.subcontrol.entity.FollowOrderHistoryEntity;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.trader.*;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static net.maku.followcom.enums.CopyTradeFlag.CPOS;
import static net.maku.followcom.enums.CopyTradeFlag.POF;
import static online.mtapi.mt4.Op.Buy;


/**
 * MT跟单对象收到信号后，平仓操作
 */
@Slf4j
@Component
@AllArgsConstructor
public class OrderCloseCopier extends AbstractOperation implements IOperationStrategy {


    private final CopierApiTradersAdmin copierApiTradersAdmin;
    private final CacheManager cacheManager;
    private final RedissonLockUtil redissonLockUtil;
    @Override
    public void operate(AbstractApiTrader trader, EaOrderInfo orderInfo, int flag) {
        String mapKey = trader.getTrader().getId() + "#" + trader.getTrader().getAccount();

        FollowTraderEntity copier = trader.getTrader();
        Long orderId = copier.getId();
        orderInfo.setSlaveReceiveCloseTime(orderInfo.getSlaveReceiveCloseTime() == null ? LocalDateTime.now() : orderInfo.getSlaveReceiveCloseTime());
        // 通过map获取平仓订单号
        log.info("[MT4跟单者:{}-{}-{}]开始尝试平仓，[喊单者:{}-{}-{}],喊单者订单信息[{}]", orderId, copier.getAccount(), copier.getServerName(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);
        // 跟单者(和当前喊单者的平仓订单)，对应的订单号。
        //取出缓存
        CachedCopierOrderInfo cachedCopierOrderInfo = null;
        long startTime = System.currentTimeMillis(); // 记录开始时间
        cachedCopierOrderInfo = (CachedCopierOrderInfo) redisUtil.hGet(Constant.FOLLOW_SUB_ORDER + mapKey, Long.toString(orderInfo.getTicket()));
        while (ObjectUtil.isEmpty(cachedCopierOrderInfo)){
            try {
                Thread.sleep(10);
                cachedCopierOrderInfo = (CachedCopierOrderInfo) redisUtil.hGet(Constant.FOLLOW_SUB_ORDER + mapKey, Long.toString(orderInfo.getTicket()));
                // 检查是否超过3秒
                if (System.currentTimeMillis() - startTime > 3000) {
                    return; // 超时直接返回
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (ObjectUtils.isEmpty(cachedCopierOrderInfo)) {
            log.info("未发现缓存" + mapKey);
            FollowSubscribeOrderEntity openOrderMapping = followSubscribeOrderService.getOne(Wrappers.<FollowSubscribeOrderEntity>lambdaQuery()
                    .eq(FollowSubscribeOrderEntity::getMasterId, orderInfo.getMasterId())
                    .eq(FollowSubscribeOrderEntity::getSlaveId, orderId)
                    .eq(FollowSubscribeOrderEntity::getMasterTicket, orderInfo.getTicket()));
            if (!ObjectUtils.isEmpty(openOrderMapping)) {
                cachedCopierOrderInfo = new CachedCopierOrderInfo(openOrderMapping);
            } else {
                cachedCopierOrderInfo = new CachedCopierOrderInfo();
            }
        }
        if (ObjectUtils.isEmpty(cachedCopierOrderInfo)||ObjectUtils.isEmpty(cachedCopierOrderInfo.getSlaveTicket())) {
            log.error("[MT4跟单者:{}-{}-{}]没有找到对应平仓订单号,因为该对应的订单开仓失败，[喊单者:{}-{}-{}],喊单者订单信息[{}]", orderId, copier.getAccount(), copier.getServerName(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);
        } else {
            closeOrder(trader, cachedCopierOrderInfo, orderInfo, flag, mapKey);
        }
    }

    /**
     * @param cachedCopierOrderInfo 缓存
     * @param orderInfo             订单信息
     * @return 是否继续尝试 true false
     */
    void closeOrder(AbstractApiTrader trader, CachedCopierOrderInfo cachedCopierOrderInfo, EaOrderInfo orderInfo, int flag, String mapKey) {
        FollowTraderEntity copier = trader.getTrader();
        Long orderId = copier.getId();
        String ip="";
        try {
            Order order = null;
            FollowTraderSubscribeEntity leaderCopier = followTraderSubscribeService.subscription(copier.getId(), orderInfo.getMasterId());
            if (leaderCopier == null) {
                throw new RuntimeException("跟随关系不存在");
            }
            log.info("平仓信息"+cachedCopierOrderInfo);
            double lots = cachedCopierOrderInfo.getSlavePosition();
            QuoteClient quoteClient=trader.quoteClient;
            if (ObjectUtil.isEmpty(trader) || ObjectUtil.isEmpty(quoteClient)
                    || !quoteClient.Connected()) {
                copierApiTradersAdmin.removeTrader(copier.getId().toString());
                ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(copier);
                if (conCodeEnum == ConCodeEnum.SUCCESS ) {
                    quoteClient=copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(copier.getId().toString()).quoteClient;
                    CopierApiTrader copierApiTrader1 = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(copier.getId().toString());
                    copierApiTrader1.setTrader(copier);
                    copier=copierApiTrader1.getTrader();
                }else {
                    log.error(trader.getTrader().getId()+"掉线异常");
                    throw new RuntimeException("登录异常"+trader.getTrader().getId());
                }
            }
            if (ObjectUtil.isEmpty(quoteClient.GetQuote(cachedCopierOrderInfo.getSlaveSymbol()))){
                //订阅
                quoteClient.Subscribe(cachedCopierOrderInfo.getSlaveSymbol());
            }
            ip=quoteClient.Host+":"+quoteClient.Port;
            double bid =0;
            double ask =0;
            int loopTimes=1;
            QuoteEventArgs quoteEventArgs = null;
            while (quoteEventArgs == null && quoteClient.Connected()) {
                quoteEventArgs = quoteClient.GetQuote(cachedCopierOrderInfo.getSlaveSymbol());
                if (++loopTimes > 20) {
                    break;
                } else {
                    Thread.sleep(50);
                }
            }
            bid =ObjectUtil.isNotEmpty(quoteEventArgs)?quoteEventArgs.Bid:0;
            ask =ObjectUtil.isNotEmpty(quoteEventArgs)?quoteEventArgs.Ask:0;
            double startPrice = trader.getTrader().getType().equals(Buy.getValue()) ? bid : ask;
            LocalDateTime startTime = LocalDateTime.now();
            log.info("平仓信息记录{}:{}:{}",cachedCopierOrderInfo.getSlaveSymbol(),cachedCopierOrderInfo.getSlaveTicket(),lots);
            long start = System.currentTimeMillis();
            if (copier.getType() == Buy.getValue()) {
                order = quoteClient.OrderClient.OrderClose(cachedCopierOrderInfo.getSlaveSymbol(), cachedCopierOrderInfo.getSlaveTicket().intValue(), cachedCopierOrderInfo.getSlavePosition(), bid, Integer.MAX_VALUE);
            } else {
                order = quoteClient.OrderClient.OrderClose(cachedCopierOrderInfo.getSlaveSymbol(), cachedCopierOrderInfo.getSlaveTicket().intValue(), cachedCopierOrderInfo.getSlavePosition(), ask, Integer.MAX_VALUE);
            }
            long end = System.currentTimeMillis();
            log.info("MT4平仓时间差 订单:"+order.Ticket+"内部时间差:"+order.closeTimeDifference+"外部时间差:"+(end-start));
            LocalDateTime endTime = LocalDateTime.now();
            if (flag == 1) {
                log.info("补单平仓开始");
            }
            log.info("[MT4跟单者{}-{}-{}]完全平仓[{}]订单成功，[喊单者{}-{}-{}],喊单者订单信息[{}]", orderId, copier.getAccount(), copier.getServerName(), cachedCopierOrderInfo.getSlaveTicket(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);

            BigDecimal leaderProfit = orderInfo.getSwap().add(orderInfo.getCommission()).add(orderInfo.getProfit()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal copierProfit = new BigDecimal(order.Swap + order.Commission + order.Profit).setScale(2, RoundingMode.HALF_UP);
            // 创建订单结果事件
            OrderResultCloseEvent event = new OrderResultCloseEvent(order, orderInfo, copier,flag,leaderProfit, copierProfit, startTime, endTime, startPrice, ip);
            ObjectMapper mapper = JacksonConfig.getObjectMapper();
            String jsonEvent = null;
            try {
                jsonEvent = mapper.writeValueAsString(event);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            // 保存到批量发送队列
            kafkaCloseMessages.add(jsonEvent);
            //删除漏单
            FollowTraderEntity master = followTraderService.getById(leaderCopier.getMasterId());
            //String mapKey = copierApiTrader.getTrader().getId() + "#" + copierApiTrader.getTrader().getAccount();
            String closekey = Constant.REPAIR_CLOSE + "：" + copier.getAccount();
            boolean closelock = redissonLockUtil.lock(closekey, 10, -1, TimeUnit.SECONDS);
            try {
                if(closelock) {
                    redisUtil.hDel(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST +"#"+copier.getPlatform()+"#"+master.getPlatform()+ "#" + leaderCopier.getSlaveAccount() + "#" + leaderCopier.getMasterAccount(), orderInfo.getTicket().toString());
                    //删除漏单redis记录
                    Object o1 = redisUtil.hGetStr(Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(), copier.getAccount().toString());
                    Map<Integer, OrderRepairInfoVO> repairInfoVOS = new HashMap();
                    if (o1!=null && o1.toString().trim().length()>0){
                        repairInfoVOS= JSONObject.parseObject(o1.toString(), Map.class);
                    }
                    repairInfoVOS.remove(orderInfo.getTicket());
                    if(repairInfoVOS==null || repairInfoVOS.size()==0){
                        redisUtil.hDel(Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(), copier.getAccount().toString());
                    }else{
                        redisUtil.hSetStr(Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(), copier.getAccount().toString(),JSONObject.toJSONString(repairInfoVOS));
                    }

                    log.info("漏平删除,key:{},key:{},val:{},订单号:{}",Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(), leaderCopier.getSlaveAccount().toString(),JSONObject.toJSONString(repairInfoVOS),orderInfo.getTicket() );
                }
            } catch (Exception e) {
            log.error("漏平检查写入异常{0}",e);
        }finally {
            redissonLockUtil.unlock(closekey);
        }

        } catch (Exception e) {
            followSubscribeOrderService.update(Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate()
                    .set(FollowSubscribeOrderEntity::getFlag, POF)
                    .set(FollowSubscribeOrderEntity::getMasterCloseTime, orderInfo.getCloseTime())
                    .set(FollowSubscribeOrderEntity::getDetectedCloseTime, orderInfo.getDetectedCloseTime())
                    .set(FollowSubscribeOrderEntity::getExtra, "[平仓失败]" + e.getMessage())
                    .ne(FollowSubscribeOrderEntity::getFlag, CPOS)
                    .eq(FollowSubscribeOrderEntity::getMasterId, orderInfo.getMasterId())
                    .eq(FollowSubscribeOrderEntity::getSlaveId, orderId)
                    .eq(FollowSubscribeOrderEntity::getMasterTicket, orderInfo.getTicket()));
            FollowOrderDetailEntity followOrderDetailEntity=followOrderDetailService.getOne(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getTraderId,copier.getId()).eq(FollowOrderDetailEntity::getOrderNo,cachedCopierOrderInfo.getSlaveTicket().intValue()));
            if (ObjectUtil.isNotEmpty(followOrderDetailEntity)){
                followOrderDetailEntity.setRemark(e.getMessage());
                followOrderDetailService.updateById(followOrderDetailEntity);
            }
            //生成日志
            FollowTraderLogEntity followTraderLogEntity = new FollowTraderLogEntity();
            followTraderLogEntity.setTraderType(TraderLogEnum.FOLLOW_OPERATION.getType());
            FollowVpsEntity followVpsEntity = followVpsService.getById(copier.getServerId());
            followTraderLogEntity.setVpsId(followVpsEntity.getId());
            followTraderLogEntity.setVpsClient(followVpsEntity.getClientId());
            followTraderLogEntity.setVpsName(followVpsEntity.getName());
            followTraderLogEntity.setCreateTime(LocalDateTime.now());
            followTraderLogEntity.setStatus(CloseOrOpenEnum.CLOSE.getValue());
            followTraderLogEntity.setType(flag == 0 ? TraderLogTypeEnum.CLOSE.getType() : TraderLogTypeEnum.REPAIR.getType());
            //跟单信息
            String remark = (flag == 0 ? FollowConstant.FOLLOW_CLOSE : FollowConstant.FOLLOW_REPAIR_CLOSE) + "【失败】策略账号=" + orderInfo.getAccount() + "单号=" + orderInfo.getTicket() +
                    "跟单账号=" + copier.getAccount() + ",单号=" + cachedCopierOrderInfo.getSlaveTicket() + ",品种=" + cachedCopierOrderInfo.getSlaveSymbol() + ",手数=" + cachedCopierOrderInfo.getSlavePosition() + ",类型=" +Op.forValue(cachedCopierOrderInfo.getSlaveType()).name()+",节点="+ip;
            followTraderLogEntity.setLogDetail(remark);
            followTraderLogEntity.setCreator(ObjectUtil.isNotEmpty(SecurityUser.getUserId())?SecurityUser.getUserId():null);
            followTraderLogService.save(followTraderLogEntity);

            redisUtil.hDel(Constant.FOLLOW_SUB_ORDER + mapKey, Long.toString(orderInfo.getTicket()));
            log.error("跟单者完全平仓订单最终尝试失败，[原因：{}],[跟单者{}-{}-{}]完全平仓{}订单失败，[喊单者{}-{}-{}],喊单者订单信息[{}]", e.getMessage(), orderId, copier.getAccount(), copier.getServerName(), cachedCopierOrderInfo.getSlaveTicket(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);
        }
    }


}