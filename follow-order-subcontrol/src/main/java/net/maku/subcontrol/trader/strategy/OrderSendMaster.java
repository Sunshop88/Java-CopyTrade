package net.maku.subcontrol.trader.strategy;

import cn.hutool.core.thread.ThreadUtil;
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
import net.maku.followcom.util.FollowConstant;
import net.maku.framework.common.constant.Constant;
import net.maku.subcontrol.entity.FollowOrderHistoryEntity;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.trader.AbstractApiTrader;
import online.mtapi.mt4.Op;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
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
            //插入历史订单
            FollowOrderHistoryEntity historyEntity = new FollowOrderHistoryEntity();
            historyEntity.setTraderId(trader.getId());
            historyEntity.setAccount(trader.getAccount());
            historyEntity.setOrderNo(orderInfo.getTicket());
            historyEntity.setType(orderInfo.getType());
            historyEntity.setOpenTime(orderInfo.getOpenTime());
            historyEntity.setCloseTime(orderInfo.getCloseTime());
            historyEntity.setSize(BigDecimal.valueOf(orderInfo.getLots()));
            historyEntity.setSymbol(orderInfo.getSymbol());
            historyEntity.setOpenPrice(BigDecimal.valueOf(orderInfo.getOpenPrice()));
            historyEntity.setClosePrice(BigDecimal.valueOf(orderInfo.getClosePrice()));
            //止损
            BigDecimal copierProfit = new BigDecimal(orderInfo.getSwap() + orderInfo.getComment() + orderInfo.getProfit()).setScale(2, RoundingMode.HALF_UP);
            historyEntity.setProfit(copierProfit);
            historyEntity.setComment(orderInfo.getComment());
            historyEntity.setSwap(orderInfo.getSwap());
            historyEntity.setMagic((int)orderInfo.getMagic());
            historyEntity.setTp(BigDecimal.valueOf(orderInfo.getTp()));
            historyEntity.setSl(BigDecimal.valueOf(orderInfo.getSl()));
            historyEntity.setCreateTime(LocalDateTime.now());
            historyEntity.setVersion(0);
            historyEntity.setPlacedType(0);
            historyEntity.setCommission(orderInfo.getCommission());
            followOrderHistoryService.save(historyEntity);
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
