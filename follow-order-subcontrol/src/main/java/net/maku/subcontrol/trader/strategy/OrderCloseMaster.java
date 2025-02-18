package net.maku.subcontrol.trader.strategy;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderLogEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.enums.*;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.MessagesService;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.vo.FixTemplateVO;
import net.maku.followcom.vo.FollowTraderVO;
import net.maku.followcom.vo.OrderRepairInfoVO;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.security.user.SecurityUser;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.trader.AbstractApiTrader;
import online.mtapi.mt4.Op;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * MT4喊单平仓操作
 */
@Slf4j
@Component
@AllArgsConstructor
public class OrderCloseMaster extends AbstractOperation implements IOperationStrategy {
    private final MessagesService messagesService;
    @Override
    public void operate(AbstractApiTrader abstractApiTrader,EaOrderInfo orderInfo,int flag) {
        FollowTraderEntity trader = abstractApiTrader.getTrader();
        //修改喊单订单记录
        FollowSubscribeOrderEntity subscribeOrderEntity = followSubscribeOrderService.getOne(new LambdaQueryWrapper<FollowSubscribeOrderEntity>().eq(FollowSubscribeOrderEntity::getMasterId,trader.getId()).eq(FollowSubscribeOrderEntity::getMasterTicket, orderInfo.getTicket()).eq(FollowSubscribeOrderEntity::getMasterOrSlave, TraderTypeEnum.MASTER_REAL.getType()));
        if (ObjectUtil.isNotEmpty(subscribeOrderEntity)){
            subscribeOrderEntity.setMasterCloseTime(orderInfo.getCloseTime());
            subscribeOrderEntity.setMasterProfit(orderInfo.getProfit());
            subscribeOrderEntity.setDetectedCloseTime(orderInfo.getDetectedCloseTime());
            subscribeOrderEntity.setExtra("平仓成功");
            followSubscribeOrderService.updateById(subscribeOrderEntity);
        }
        //修改喊单的跟单订单记录
        List<FollowSubscribeOrderEntity> subscribeOrderEntityList = followSubscribeOrderService.list(new LambdaQueryWrapper<FollowSubscribeOrderEntity>().eq(FollowSubscribeOrderEntity::getMasterTicket, orderInfo.getTicket()).eq(FollowSubscribeOrderEntity::getMasterId,trader.getId()).eq(FollowSubscribeOrderEntity::getMasterOrSlave, TraderTypeEnum.SLAVE_REAL.getType()));
        subscribeOrderEntityList.forEach(o->{
            o.setMasterCloseTime(orderInfo.getCloseTime());
            o.setMasterProfit(orderInfo.getProfit());
            o.setDetectedCloseTime(orderInfo.getDetectedCloseTime());
            followSubscribeOrderService.updateById(o);
        });
        //查看跟单关系
        List<FollowTraderSubscribeEntity> subscribeEntityList = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterId, orderInfo.getMasterId()));
        //保存所需要平仓的用户到redis，用备注记录 set类型存储
        subscribeEntityList.forEach(o->{
            FollowTraderEntity follow=followTraderService.getFollowById(o.getSlaveId());
            //创建平仓redis记录
            redisUtil.hSet(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST+"#"+follow.getPlatform()+"#"+trader.getPlatform()+"#"+o.getSlaveAccount()+"#"+o.getMasterAccount(),orderInfo.getTicket().toString(),orderInfo);
            //发送漏单通知
            FollowTraderVO master = followTraderService.get(orderInfo.getMasterId());
            messagesService.isRepairClose(orderInfo,follow,master);
            //删除跟单redis记录
            redisUtil.hDel(Constant.FOLLOW_REPAIR_SEND+ FollowConstant.LOCAL_HOST+"#"+follow.getPlatform()+"#"+trader.getPlatform()+"#"+o.getSlaveAccount()+"#"+o.getMasterAccount(),orderInfo.getTicket().toString());
           //删除漏单redis记录
            Object o1 = redisUtil.hGetStr(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), follow.getAccount().toString());
            Map<Integer,OrderRepairInfoVO> repairInfoVOS = new HashMap();
            if (o1!=null && o1.toString().trim().length()>0){
                repairInfoVOS= JSONObject.parseObject(o1.toString(), Map.class);
            }
            repairInfoVOS.remove(orderInfo.getTicket());
            if(repairInfoVOS==null || repairInfoVOS.size()==0){
                redisUtil.hDel(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(),follow.getAccount().toString());
            }else{
                redisUtil.hSetStr(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), follow.getAccount().toString(),JSONObject.toJSONString(repairInfoVOS));
            }

            log.info("漏单删除,key:{},key:{},订单号:{},val:{},",Constant.REPAIR_SEND +master.getAccount() + ":" + master.getId(), follow.getAccount(),orderInfo.getTicket(),JSONObject.toJSONString(repairInfoVOS) );

        });

        ThreadPoolUtils.getExecutor().execute(()->{
            //生成日志
            FollowTraderLogEntity followTraderLogEntity = new FollowTraderLogEntity();
            followTraderLogEntity.setTraderType(TraderLogEnum.FOLLOW_OPERATION.getType());
            FollowVpsEntity followVpsEntity = followVpsService.getById(trader.getServerId());
            followTraderLogEntity.setVpsId(followVpsEntity.getId());
            followTraderLogEntity.setVpsClient(followVpsEntity.getClientId());
            followTraderLogEntity.setVpsName(followVpsEntity.getName());
            followTraderLogEntity.setCreateTime(LocalDateTime.now());
            followTraderLogEntity.setType(TraderLogTypeEnum.CLOSE.getType());
            followTraderLogEntity.setCreator(ObjectUtil.isNotEmpty(SecurityUser.getUserId())?SecurityUser.getUserId():null);
            String remark= FollowConstant.FOLLOW_CLOSE+"策略账号="+orderInfo.getAccount()+",单号="+orderInfo.getTicket()+",品种="+orderInfo.getSymbol()+",手数="+orderInfo.getLots()+",类型="+ Op.forValue(orderInfo.getType()).name();
            followTraderLogEntity.setLogDetail(remark);
            followTraderLogService.save(followTraderLogEntity);
        });
    }



}
