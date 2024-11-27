package net.maku.subcontrol.trader.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderLogEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderLogEnum;
import net.maku.followcom.enums.TraderLogTypeEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowTraderLogService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.util.FollowConstant;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.service.FollowOrderHistoryService;
import net.maku.subcontrol.service.FollowSubscribeOrderService;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.trader.LeaderApiTrader;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import online.mtapi.mt4.Op;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * MT4 跟单者处理开仓信号策略
 *
 * @author samson bruce
 * @since 2023/04/14
 */
@Slf4j
@Component
@AllArgsConstructor
public class OrderSendMaster extends AbstractOperation implements IOperationStrategy {

    /**
     * 收到开仓信号处理操作
     */
    @Override
    public void operate(AbstractApiTrader abstractApiTrader, EaOrderInfo orderInfo,int flag) {
        FollowTraderEntity trader = abstractApiTrader.getTrader();
        //查看跟单关系
        List<FollowTraderSubscribeEntity> subscribeEntityList = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterId, orderInfo.getMasterId())
                .eq(FollowTraderSubscribeEntity::getFollowStatus, CloseOrOpenEnum.OPEN.getValue())
                .eq(FollowTraderSubscribeEntity::getFollowOpen,CloseOrOpenEnum.OPEN.getValue()));
        //保存所需要下单的用户到redis，用备注记录 set类型存储
        String comment = comment(orderInfo);
        orderInfo.setSlaveComment(comment);
        //保存下单信息
        subscribeEntityList.forEach(o->{
            redisUtil.lSet(Constant.FOLLOW_REPAIR_SEND+o.getId(),orderInfo);
        });
        threeStrategyThreadPoolExecutor.schedule(()->{
            //生成记录
            FollowSubscribeOrderEntity openOrderMapping = new FollowSubscribeOrderEntity(orderInfo,trader);
            followSubscribeOrderService.save(openOrderMapping);
            //生成日志
            FollowTraderLogEntity followTraderLogEntity = new FollowTraderLogEntity();
            followTraderLogEntity.setTraderType(TraderLogEnum.FOLLOW_OPERATION.getType());
            FollowVpsEntity followVpsEntity = followVpsService.getById(trader.getServerId());
            followTraderLogEntity.setVpsId(followVpsEntity.getId());
            followTraderLogEntity.setVpsClient(followVpsEntity.getClientId());
            followTraderLogEntity.setVpsName(followVpsEntity.getName());
            followTraderLogEntity.setCreateTime(LocalDateTime.now());
            followTraderLogEntity.setType(TraderLogTypeEnum.SEND.getType());
            String remark= FollowConstant.FOLLOW_SEND+"策略账号="+orderInfo.getAccount()+",单号="+orderInfo.getTicket()+",品种="+orderInfo.getSymbol()+",手数="+orderInfo.getLots()+",类型="+ Op.forValue(orderInfo.getType()).name();
            followTraderLogEntity.setLogDetail(remark);
            followTraderLogService.save(followTraderLogEntity);
        },100, TimeUnit.MILLISECONDS);
    }
}
