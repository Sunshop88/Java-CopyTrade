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
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.service.FollowSlaveService;
import net.maku.subcontrol.trader.CopierApiTrader;
import net.maku.subcontrol.trader.CopierApiTradersAdmin;
import net.maku.subcontrol.trader.strategy.OrderCloseCopier;
import net.maku.subcontrol.trader.strategy.OrderSendCopier;
import net.maku.subcontrol.vo.RepairSendVO;
import online.mtapi.mt4.Order;
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
    private final FollowVpsService followVpsService;
    private final FollowTraderService followTraderService;
    @Override
    public Boolean repairSend(RepairSendVO repairSendVO) {
        FollowVpsEntity vps = followVpsService.getVps(FollowConstant.LOCAL_HOST);
        if(ObjectUtil.isEmpty(vps) || vps.getIsActive().equals(CloseOrOpenEnum.CLOSE.getValue()) ) {
            throw new ServerException("VPS已关闭");
        }
        FollowTraderSubscribeEntity subscription = followTraderSubscribeService.subscription(repairSendVO.getSlaveId(), repairSendVO.getMasterId());
        if (repairSendVO.getType().equals(TraderRepairEnum.ALL.getType())) {
            CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(repairSendVO.getSlaveId().toString());
            Order[] orders = copierApiTrader.quoteClient.GetOpenedOrders();

            if (ObjectUtil.isEmpty(copierApiTrader)){
                throw new ServerException("账号异常请重连");
            }
            if (subscription.getFollowStatus().equals(CloseOrOpenEnum.OPEN.getValue())&&subscription.getFollowOpen().equals(CloseOrOpenEnum.OPEN.getValue())){
                FollowTraderEntity slave = followTraderService.getFollowById(repairSendVO.getSlaveId());
                FollowTraderEntity master = followTraderService.getFollowById(repairSendVO.getMasterId());
                //下单
                Map<Object,Object> sendRepair=redisUtil.hGetAll(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST+"#"+slave.getPlatform()+"#"+master.getPlatform()+"#"+subscription.getSlaveAccount()+"#"+subscription.getMasterAccount());
                List<Object> sendRepairToExtract = new ArrayList<>();
                for (Object repairObj : sendRepair.keySet()) {
                    EaOrderInfo repairComment = (EaOrderInfo) sendRepair.get(repairObj);
                    boolean existsInActive = Arrays.stream(orders).toList().stream().anyMatch(order ->String.valueOf(repairComment.getTicket()).equalsIgnoreCase(String.valueOf(order.MagicNumber)));
                    if (!existsInActive) {
                        sendRepairToExtract.add(repairComment);
                    }
                }
                sendRepairToExtract.stream().toList().forEach(o->{
                    EaOrderInfo eaOrderInfo = (EaOrderInfo) o;
                    orderSendCopier.operate(copierApiTrader,eaOrderInfo,1);
                });
            }
            if (subscription.getFollowStatus().equals(CloseOrOpenEnum.OPEN.getValue())&&subscription.getFollowClose().equals(CloseOrOpenEnum.OPEN.getValue())) {
                FollowTraderEntity slave = followTraderService.getFollowById(repairSendVO.getSlaveId());
                FollowTraderEntity master = followTraderService.getFollowById(repairSendVO.getMasterId());
                List<Object> closeRepairToExtract = new ArrayList<>();
                Map<Object,Object> closeRepair=redisUtil.hGetAll(Constant.FOLLOW_REPAIR_CLOSE+ FollowConstant.LOCAL_HOST+"#"+slave.getPlatform()+"#"+master.getPlatform()+"#"+subscription.getSlaveAccount()+"#"+subscription.getMasterAccount());

                for (Object repairObj : closeRepair.keySet()) {

                    EaOrderInfo repairComment = (EaOrderInfo) closeRepair.get(repairObj);
                    boolean existsInActive = Arrays.stream(orders).toList().stream().anyMatch(order -> String.valueOf(repairComment.getTicket()).equalsIgnoreCase(String.valueOf(order.MagicNumber)));
                    if (existsInActive) {
                        closeRepairToExtract.add(repairComment);
                    }
                }
                closeRepairToExtract.stream().toList().forEach(o->{
                    EaOrderInfo eaOrderInfo = (EaOrderInfo)  o;
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
                        FollowTraderEntity slave = followTraderService.getFollowById(repairSendVO.getSlaveId());
                        FollowTraderEntity master = followTraderService.getFollowById(repairSendVO.getMasterId());
                        //获取redis内的下单信息
                        if (ObjectUtil.isNotEmpty(redisUtil.hGet(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST+"#"+slave.getPlatform()+"#"+master.getPlatform()+"#"+traderSubscribeEntity.getSlaveAccount()+"#"+traderSubscribeEntity.getMasterAccount(),repairSendVO.getOrderNo().toString()))){
                            EaOrderInfo objects = (EaOrderInfo)redisUtil.hGet(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST+"#"+slave.getPlatform()+"#"+master.getPlatform()+"#"+traderSubscribeEntity.getSlaveAccount()+"#"+traderSubscribeEntity.getMasterAccount(),repairSendVO.getOrderNo().toString());
                            orderSendCopier.operate(copierApiTrader,objects,1);
                        }else {
                            throw new ServerException("暂无订单需处理");
                        }
                    }else {
                        throw new ServerException("请开启补仓开关");
                    }
                }else {
                    if (subscription.getFollowStatus().equals(CloseOrOpenEnum.OPEN.getValue())&&subscription.getFollowClose().equals(CloseOrOpenEnum.OPEN.getValue())) {
                        FollowTraderEntity slave = followTraderService.getFollowById(repairSendVO.getSlaveId());
                        FollowTraderEntity master = followTraderService.getFollowById(repairSendVO.getMasterId());
                        if (ObjectUtil.isNotEmpty(redisUtil.hGet(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST +"#"+slave.getPlatform()+"#"+master.getPlatform()+ "#" + traderSubscribeEntity.getSlaveAccount() + "#" + traderSubscribeEntity.getMasterAccount(), repairSendVO.getOrderNo().toString()))) {
                            EaOrderInfo objects = (EaOrderInfo) redisUtil.hGet(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST +"#"+slave.getPlatform()+"#"+master.getPlatform()+ "#" + traderSubscribeEntity.getSlaveAccount() + "#" + traderSubscribeEntity.getMasterAccount(), repairSendVO.getOrderNo().toString());
                            orderCloseCopier.operate(copierApiTrader, objects, 1);
                            ThreadPoolUtils.getExecutor().execute(()->{
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                                //删除平仓redis记录
                                String mapKey = copierApiTrader.getTrader().getId() + "#" + copierApiTrader.getTrader().getAccount();
                                if (ObjectUtil.isEmpty(redisUtil.hGet(Constant.FOLLOW_SUB_ORDER + mapKey, Long.toString(objects.getTicket())))){
                                    redisUtil.hDel(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST +"#"+slave.getPlatform()+"#"+master.getPlatform()+ "#" + traderSubscribeEntity.getSlaveAccount() + "#" + traderSubscribeEntity.getMasterAccount(), repairSendVO.getOrderNo().toString());
                                }
                            });
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

    @Override
    public Boolean batchRepairSend(List<RepairSendVO> repairSendVO) {
        repairSendVO.forEach(repair -> {
            repairSend(repair);
        });
        return true;
    }
}
