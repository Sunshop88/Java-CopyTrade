package net.maku.subcontrol.callable;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderLogEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.enums.CopyTradeFlag;
import net.maku.followcom.enums.TraderLogEnum;
import net.maku.followcom.enums.TraderLogTypeEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowTraderLogService;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.service.impl.FollowTraderLogServiceImpl;
import net.maku.followcom.service.impl.FollowVpsServiceImpl;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.service.FollowSubscribeOrderService;
import net.maku.subcontrol.service.impl.FollowSubscribeOrderServiceImpl;
import net.maku.subcontrol.trader.OrderResultEvent;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class KafkaMessageConsumer {
    private final FollowSubscribeOrderService openOrderMappingService=SpringContextUtils.getBean(FollowSubscribeOrderServiceImpl.class);
    private final RedisUtil redisUtil=SpringContextUtils.getBean(RedisUtil.class);
    private final FollowVpsService followVpsService=SpringContextUtils.getBean(FollowVpsServiceImpl.class);
    private final FollowTraderLogService followTraderLogService=SpringContextUtils.getBean(FollowTraderLogServiceImpl.class);
    /**
     * 消费者监听指定 Topic
     */
    @KafkaListener(topics = "order-send", groupId = "order-group")
    public void consumeMessageMasterSend(String o) {
        OrderResultEvent orderResultEvent = JSON.parseObject(o,OrderResultEvent.class);
        // 处理消息逻辑
        handleOrderResult(orderResultEvent.getOrder(),orderResultEvent.getOrderInfo(),orderResultEvent.getOpenOrderMapping(),orderResultEvent.getCopier(),orderResultEvent.getFlag());
    }

    private void handleOrderResult(Order order, EaOrderInfo orderInfo,
                                   FollowSubscribeOrderEntity openOrderMapping, FollowTraderEntity copier, Integer flag) {
        // 处理下单成功结果，记录日志和缓存
        log.info("[MT4跟单者:{}] 下单成功, 订单: {}", copier.getAccount(), order);
        openOrderMapping.setCopierOrder(order, orderInfo);
        openOrderMapping.setFlag(CopyTradeFlag.OS);
        openOrderMapping.setExtra("[开仓]即时价格成交");

        // 数据持久化操作
        persistOrderMapping(openOrderMapping);

        // 缓存跟单数据
        cacheCopierOrder(orderInfo, order);

        // 日志记录
        logFollowOrder(copier, orderInfo, openOrderMapping, flag);
    }


    private void handleOrderFailure(EaOrderInfo orderInfo, FollowSubscribeOrderEntity openOrderMapping, Exception e) {
        openOrderMapping.setSlaveType(Op.forValue(orderInfo.getType()).getValue());
        openOrderMapping.setSlaveTicket(null);
        openOrderMapping.setFlag(CopyTradeFlag.OF1);
        openOrderMapping.setExtra("[开仓]即时价格开仓失败" + e.getMessage());

        // 数据持久化失败状态
        persistOrderMapping(openOrderMapping);
    }

    private void persistOrderMapping(FollowSubscribeOrderEntity openOrderMapping) {
        openOrderMappingService.saveOrUpdate(openOrderMapping, Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate()
                .eq(FollowSubscribeOrderEntity::getMasterId, openOrderMapping.getMasterId())
                .eq(FollowSubscribeOrderEntity::getMasterTicket, openOrderMapping.getMasterTicket())
                .eq(FollowSubscribeOrderEntity::getSlaveId, openOrderMapping.getSlaveId()));
    }

    private void cacheCopierOrder(EaOrderInfo orderInfo, Order order) {
        CachedCopierOrderInfo cachedOrderInfo = new CachedCopierOrderInfo(order);
        String mapKey=orderInfo.getMasterId()+"#"+orderInfo.getTicket();
        redisUtil.hset(Constant.FOLLOW_SUB_ORDER + mapKey, Long.toString(orderInfo.getTicket()), cachedOrderInfo, 0);
    }

    private void logFollowOrder(FollowTraderEntity copier, EaOrderInfo orderInfo, FollowSubscribeOrderEntity openOrderMapping, Integer flag) {
        FollowTraderLogEntity logEntity = new FollowTraderLogEntity();
        FollowVpsEntity followVpsEntity = followVpsService.getById(copier.getServerId());
        logEntity.setVpsClient(followVpsEntity.getClientId());
        logEntity.setVpsId(copier.getServerId());
        logEntity.setVpsName(copier.getServerName());
        logEntity.setTraderType(TraderLogEnum.FOLLOW_OPERATION.getType());
        logEntity.setCreateTime(LocalDateTime.now());
        logEntity.setType(flag == 0 ? TraderLogTypeEnum.SEND.getType() : TraderLogTypeEnum.REPAIR.getType());
        String remark = (flag == 0 ? FollowConstant.FOLLOW_SEND : FollowConstant.FOLLOW_REPAIR_SEND)
                + ", 策略账号=" + orderInfo.getAccount()
                + ", 单号=" + orderInfo.getTicket()
                + ", 跟单账号=" + openOrderMapping.getSlaveAccount()
                + ", 品种=" + openOrderMapping.getSlaveSymbol()
                + ", 手数=" + openOrderMapping.getSlaveLots()
                + ", 类型=" + Op.forValue(openOrderMapping.getSlaveType()).name();
        logEntity.setLogDetail(remark);
        followTraderLogService.save(logEntity);
    }
}
