package net.maku.subcontrol.trader;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowSubscribeOrderEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.pojo.EaOrderInfo;
import net.maku.subcontrol.service.IOperationStrategy;
import online.mtapi.mt4.Op;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.util.ObjectUtils;

/**
 * MT跟单对象收到信号后，修改操作
 */
@Slf4j
public class OrderModifySlave extends AbstractOperation implements IOperationStrategy {
    FollowTraderEntity copier;
    CopierApiTrader copierApiTrader;

    public OrderModifySlave(CopierApiTrader copierApiTrader) {
        super(copierApiTrader.getTrader());
        this.copierApiTrader = copierApiTrader;
        this.copier = this.copierApiTrader.getTrader();
    }

    @Override
    public void operate(ConsumerRecord<String, Object> record, int retry) {
        EaOrderInfo orderInfo = (EaOrderInfo) record.value();
        CachedCopierOrderInfo cachedCopierOrderInfo = (CachedCopierOrderInfo) redisUtil.hGet(mapKey, Long.toString(orderInfo.getTicket()));
        if (!ObjectUtils.isEmpty(cachedCopierOrderInfo)) {
            try {
                FollowSubscribeOrderEntity openOrderMapping = openOrderMappingService.getOne(Wrappers.<FollowSubscribeOrderEntity>lambdaQuery()
                        .eq(FollowSubscribeOrderEntity::getSlaveId, String.valueOf(copier.getId()))
                        .eq(FollowSubscribeOrderEntity::getMasterTicket, orderInfo.getTicket()));
                cachedCopierOrderInfo = ObjectUtils.isEmpty(openOrderMapping) ? new CachedCopierOrderInfo() : new CachedCopierOrderInfo(openOrderMapping);
                if (cachedCopierOrderInfo.getSlaveTicket() != null) {
                    FollowTraderSubscribeEntity leaderCopier = leaderCopierService.subscription(copier.getId(), Long.valueOf(orderInfo.getMasterId()));
                    if (leaderCopier.getTpSl().equals(CloseOrOpenEnum.OPEN.getValue())) {
                        try {
                            Op op = Op.forValue(cachedCopierOrderInfo.getSlaveType());
                            copierApiTrader.orderClient.OrderModify(op, cachedCopierOrderInfo.getSlaveTicket().intValue(), 0, orderInfo.getSl(), orderInfo.getTp(), null);
                            log.info("[MT跟单者:{}-{}-{}]修改报价sl:{},tp:{}成功", copier.getId(), copier.getAccount(), copier.getServerName(), orderInfo.getSl(), orderInfo.getTp());
                        } catch (Exception ignored) {
                        }
                    } else {
                        log.info("[MT跟单者:{}-{}-{}]不需要修改报价sl:{},tp:{}成功", copier.getId(), copier.getAccount(), copier.getServerName(), orderInfo.getSl(), orderInfo.getTp());
                    }
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }
}
