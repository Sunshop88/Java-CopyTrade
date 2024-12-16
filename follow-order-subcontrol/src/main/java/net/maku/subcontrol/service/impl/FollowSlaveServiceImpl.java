package net.maku.subcontrol.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.TraderRepairEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.vo.FollowRedisTraderVO;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.cache.RedissonLockUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.subcontrol.service.FollowSlaveService;
import net.maku.subcontrol.trader.CopierApiTrader;
import net.maku.subcontrol.trader.CopierApiTradersAdmin;
import net.maku.subcontrol.trader.strategy.OrderCloseCopier;
import net.maku.subcontrol.trader.strategy.OrderSendCopier;
import net.maku.subcontrol.vo.FollowOrderActiveSocketVO;
import net.maku.subcontrol.vo.RepairSendVO;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

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
                    if (ObjectUtil.isNotEmpty(redisUtil.hGet(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST+"#"+traderSubscribeEntity.getSlaveAccount()+"#"+traderSubscribeEntity.getMasterAccount(),repairSendVO.getOrderNo().toString()))){
                        EaOrderInfo objects = (EaOrderInfo)redisUtil.hGet(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST+"#"+traderSubscribeEntity.getSlaveAccount()+"#"+traderSubscribeEntity.getMasterAccount(),repairSendVO.getOrderNo().toString());
                        orderSendCopier.operate(copierApiTrader,objects,1);
                    }else {
                        throw new ServerException("暂无订单需处理");
                    }
                }else {
                    if (ObjectUtil.isNotEmpty(redisUtil.hGet(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST+"#"+traderSubscribeEntity.getSlaveAccount()+"#"+traderSubscribeEntity.getMasterAccount(),repairSendVO.getOrderNo().toString()))) {
                        EaOrderInfo objects = (EaOrderInfo) redisUtil.hGet(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST+"#"+traderSubscribeEntity.getSlaveAccount()+"#"+traderSubscribeEntity.getMasterAccount(),repairSendVO.getOrderNo().toString());
                        orderCloseCopier.operate(copierApiTrader,objects,1);
                        redisUtil.hDel(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST+"#"+traderSubscribeEntity.getSlaveAccount()+"#"+traderSubscribeEntity.getMasterAccount(),repairSendVO.getOrderNo().toString());
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
