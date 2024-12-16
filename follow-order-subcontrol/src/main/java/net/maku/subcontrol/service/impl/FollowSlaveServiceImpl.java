package net.maku.subcontrol.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderRepairEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
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

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
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
        FollowTraderSubscribeEntity subscription = followTraderSubscribeService.subscription(repairSendVO.getSlaveId(), repairSendVO.getMasterId());
        if (repairSendVO.getType().equals(TraderRepairEnum.ALL.getType())) {
            FollowTraderSubscribeEntity traderSubscribeEntity = followTraderSubscribeService.getOne(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterId, repairSendVO.getMasterId()).eq(FollowTraderSubscribeEntity::getSlaveId, repairSendVO.getSlaveId()));
            CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(repairSendVO.getSlaveId().toString());
            if (ObjectUtil.isEmpty(copierApiTrader)){
                throw new ServerException("账号异常请重连");
            }
            if (subscription.getFollowStatus().equals(CloseOrOpenEnum.OPEN.getValue())&&subscription.getFollowOpen().equals(CloseOrOpenEnum.OPEN.getValue())){
                //下单
                Map<Object, Object> map = redisUtil.hGetAll(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST + "#" + traderSubscribeEntity.getSlaveAccount() + "#" + traderSubscribeEntity.getMasterAccount());
                map.keySet().stream().toList().forEach(o->{
                    EaOrderInfo eaOrderInfo = (EaOrderInfo)  map.get(o);
                    orderSendCopier.operate(copierApiTrader,eaOrderInfo,1);
                });
            }
            if (subscription.getFollowStatus().equals(CloseOrOpenEnum.OPEN.getValue())&&subscription.getFollowClose().equals(CloseOrOpenEnum.OPEN.getValue())) {
                //平仓
                Map<Object, Object> mapclose = redisUtil.hGetAll(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST + "#" + traderSubscribeEntity.getSlaveAccount() + "#" + traderSubscribeEntity.getMasterAccount());
                mapclose.keySet().stream().toList().forEach(o->{
                    EaOrderInfo eaOrderInfo = (EaOrderInfo)  mapclose.get(o);
                    orderCloseCopier.operate(copierApiTrader,eaOrderInfo,1);
                });
            }
            return true;
        }else {
            return repair(repairSendVO,subscription);
        }
    }

    private Boolean repair(RepairSendVO repairSendVO,FollowTraderSubscribeEntity subscription){
        //避免重复点击
        if (redissonLockUtil.tryLockForShortTime(repairSendVO.getOrderNo().toString(), 0, 2, TimeUnit.SECONDS)) {
            try {
                CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(repairSendVO.getSlaveId().toString());
                if (ObjectUtil.isEmpty(copierApiTrader)){
                    throw new ServerException("账号异常请重连");
                }
                FollowTraderSubscribeEntity traderSubscribeEntity = followTraderSubscribeService.getOne(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterId, repairSendVO.getMasterId()).eq(FollowTraderSubscribeEntity::getSlaveId, repairSendVO.getSlaveId()));
                if (repairSendVO.getType().equals(TraderRepairEnum.SEND.getType())){
                    if (subscription.getFollowStatus().equals(CloseOrOpenEnum.OPEN.getValue())&&subscription.getFollowOpen().equals(CloseOrOpenEnum.OPEN.getValue())) {
                        //获取redis内的下单信息
                        if (ObjectUtil.isNotEmpty(redisUtil.hGet(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST+"#"+traderSubscribeEntity.getSlaveAccount()+"#"+traderSubscribeEntity.getMasterAccount(),repairSendVO.getOrderNo().toString()))){
                            EaOrderInfo objects = (EaOrderInfo)redisUtil.hGet(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST+"#"+traderSubscribeEntity.getSlaveAccount()+"#"+traderSubscribeEntity.getMasterAccount(),repairSendVO.getOrderNo().toString());
                            orderSendCopier.operate(copierApiTrader,objects,1);
                        }else {
                            throw new ServerException("暂无订单需处理");
                        }
                    }else {
                        throw new ServerException("请开启补仓开关");
                    }
                }else {
                    if (subscription.getFollowStatus().equals(CloseOrOpenEnum.OPEN.getValue())&&subscription.getFollowClose().equals(CloseOrOpenEnum.OPEN.getValue())) {
                        if (ObjectUtil.isNotEmpty(redisUtil.hGet(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST + "#" + traderSubscribeEntity.getSlaveAccount() + "#" + traderSubscribeEntity.getMasterAccount(), repairSendVO.getOrderNo().toString()))) {
                            EaOrderInfo objects = (EaOrderInfo) redisUtil.hGet(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST + "#" + traderSubscribeEntity.getSlaveAccount() + "#" + traderSubscribeEntity.getMasterAccount(), repairSendVO.getOrderNo().toString());
                            orderCloseCopier.operate(copierApiTrader, objects, 1);
                            redisUtil.hDel(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST + "#" + traderSubscribeEntity.getSlaveAccount() + "#" + traderSubscribeEntity.getMasterAccount(), repairSendVO.getOrderNo().toString());
                        } else {
                            throw new ServerException("暂无订单需处理");
                        }
                    }else {
                        throw new ServerException("请开启补平开关");
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
