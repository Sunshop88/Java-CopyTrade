package net.maku.subcontrol.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.TraderRepairEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.*;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.cache.RedissonLockUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.subcontrol.service.FollowSlaveService;
import net.maku.subcontrol.trader.CopierApiTrader;
import net.maku.subcontrol.trader.CopierApiTradersAdmin;
import net.maku.subcontrol.trader.strategy.OrderCloseCopier;
import net.maku.subcontrol.trader.strategy.OrderSendCopier;
import net.maku.subcontrol.vo.RepairSendVO;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class FollowSlaveServiceImpl implements FollowSlaveService {
    private final CopierApiTradersAdmin copierApiTradersAdmin;
    private final RedisUtil redisUtil;
    private final FollowTraderSubscribeService followTraderSubscribeService;
    private final RedissonLockUtil redissonLockUtil;
    private final OrderSendCopier orderSendCopier;
    private final OrderCloseCopier orderCloseCopier;

    @Override
    public Boolean repairSend(RepairSendVO repairSendVO) {
        //避免重复点击
        if (redissonLockUtil.tryLockForShortTime(repairSendVO.getOrderNo().toString(), 0, 2, TimeUnit.SECONDS)) {
            try {
                CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(repairSendVO.getSlaveId().toString());
                if (ObjectUtil.isEmpty(copierApiTrader)){
                    throw new ServerException("漏单处理异常，账号不正确");
                }
                FollowTraderSubscribeEntity traderSubscribeEntity = followTraderSubscribeService.getOne(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterId, repairSendVO.getMasterId()).eq(FollowTraderSubscribeEntity::getSlaveId, repairSendVO.getSlaveId()));
                if (repairSendVO.getType().equals(TraderRepairEnum.SEND.getType())){
                    //获取redis内的下单信息
                    List<Object> objects = redisUtil.lGet(Constant.FOLLOW_REPAIR_SEND + traderSubscribeEntity.getId(),0,-1);
                    Optional<Object> first = objects.stream().filter(o -> {
                        EaOrderInfo eaOrderInfo = (EaOrderInfo) o;
                        return eaOrderInfo.getTicket().equals(repairSendVO.getOrderNo());
                    }).toList().stream().findFirst();
                    if (first.isPresent()){
                        orderSendCopier.operate(copierApiTrader,(EaOrderInfo) first.get(),1);
                        redisUtil.lRemove(Constant.FOLLOW_REPAIR_SEND + traderSubscribeEntity.getId(),1,first.get());
                    }else {
                        throw new ServerException("暂无订单需处理");
                    }
                }else {
                    //获取redis内的平仓信息
                    List<Object> objects = redisUtil.lGet(Constant.FOLLOW_REPAIR_CLOSE + traderSubscribeEntity.getId(),0,-1);
                    Optional<Object> first = objects.stream().filter(o -> ((EaOrderInfo)o).getTicket().equals(repairSendVO.getOrderNo())).toList().stream().findFirst();
                    if (first.isPresent()){
                        orderCloseCopier.operate(copierApiTrader,(EaOrderInfo) first.get(),1);
                        redisUtil.lRemove(Constant.FOLLOW_REPAIR_CLOSE + traderSubscribeEntity.getId(),1,first.get());
                    }else {
                        throw new ServerException("暂无订单需处理");
                    }
                }
                return true;
            } finally {
                if (redissonLockUtil.isLockedByCurrentThread(repairSendVO.getOrderNo().toString())) {
                    redissonLockUtil.unlock(repairSendVO.getOrderNo().toString());
                }
            }
        } else {
            throw new ServerException("操作过于频繁，请稍后再试");
        }
    }
}
