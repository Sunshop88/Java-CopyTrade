package net.maku.subcontrol.trader.strategy;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderLogEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.enums.MessagesTypeEnum;
import net.maku.followcom.enums.TraderLogEnum;
import net.maku.followcom.enums.TraderLogTypeEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.MessagesService;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.vo.FixTemplateVO;
import net.maku.followcom.vo.FollowTraderVO;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.security.user.SecurityUser;
import net.maku.subcontrol.entity.FollowOrderHistoryEntity;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.trader.AbstractApiTrader;
import online.mtapi.mt4.Op;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;


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
      private final MessagesService messagesService;

    /**
     * 收到开仓信号处理操作
     */
    @Override
    public void operate(AbstractApiTrader abstractApiTrader, EaOrderInfo orderInfo, int flag) {
        FollowTraderEntity trader = abstractApiTrader.getTrader();
        //查看跟单关系
        List<FollowTraderSubscribeEntity> subscribeEntityList = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterId, orderInfo.getMasterId()));
        //保存所需要下单的用户到redis，用魔术号记录 set类型存储
        //保存下单信息
        subscribeEntityList.forEach(o -> {
            FollowTraderEntity follow = followTraderService.getFollowById(o.getSlaveId());
            redisUtil.hSet(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST+"#"+follow.getPlatform()+"#"+trader.getPlatform()+"#"+o.getSlaveAccount()+"#"+o.getMasterAccount(), orderInfo.getTicket().toString(),orderInfo);
            //发送漏单通知
            FollowTraderVO master = followTraderService.get(orderInfo.getMasterId());
            messagesService.isRepairSend(orderInfo,follow,master,null);
        });
   /*         FollowTraderVO master = followTraderService.get(orderInfo.getMasterId());
            FixTemplateVO vo = FixTemplateVO.builder().templateType(MessagesTypeEnum.MISSING_ORDERS_NOTICE.getCode()).
                    vpsName(follow.getServerName())
                    .source(o.getMasterAccount())
                    .sourceRemarks(master.getRemark())
                    .follow(follow.getAccount())
                    .symbol(orderInfo.getSymbol())
                    .type(Constant.NOTICE_MESSAGE_BUY).build();*/

        ThreadPoolUtils.getExecutor().execute(() -> {
            //生成记录
            FollowSubscribeOrderEntity openOrderMapping = new FollowSubscribeOrderEntity(orderInfo, trader);
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
            String remark = FollowConstant.FOLLOW_SEND + "策略账号=" + orderInfo.getAccount() + ",单号=" + orderInfo.getTicket() + ",品种=" + orderInfo.getSymbol() + ",手数=" + orderInfo.getLots() + ",类型=" + Op.forValue(orderInfo.getType()).name();
            followTraderLogEntity.setLogDetail(remark);
            followTraderLogEntity.setCreator(ObjectUtil.isNotEmpty(SecurityUser.getUserId())?SecurityUser.getUserId():null);
            followTraderLogService.save(followTraderLogEntity);
        });
    }

}
