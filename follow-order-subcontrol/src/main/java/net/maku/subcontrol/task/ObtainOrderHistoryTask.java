package net.maku.subcontrol.task;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.service.FollowOrderDetailService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.utils.Result;
import net.maku.subcontrol.service.FollowOrderHistoryService;
import net.maku.subcontrol.trader.CopierApiTrader;
import net.maku.subcontrol.trader.CopierApiTradersAdmin;
import net.maku.subcontrol.trader.LeaderApiTrader;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import online.mtapi.mt4.QuoteClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Author:  zsd
 * Date:  2024/12/4/周三 10:06
 */
@Slf4j
@Component
@AllArgsConstructor
public class ObtainOrderHistoryTask {

    private final FollowOrderHistoryService followOrderHistoryService;
    private final FollowTraderService followTraderService;
    private final LeaderApiTradersAdmin leaderApiTradersAdmin;
    private final CopierApiTradersAdmin copierApiTradersAdmin;
    private final FollowOrderDetailService followOrderDetailService;


    @Scheduled(cron = "0 0 * ? * *")
    public void getOrderHistory(){
        //1.获取所有账号
        List<FollowTraderEntity> list = followTraderService.list(Wrappers.<FollowTraderEntity>lambdaQuery()
                .eq(FollowTraderEntity::getIpAddr, FollowConstant.LOCAL_HOST)
                .eq(FollowTraderEntity::getDeleted, CloseOrOpenEnum.CLOSE.getValue())
                .orderByAsc(FollowTraderEntity::getCreateTime));
        //获取mt4客户端quoteClient
        List<FollowTraderEntity> newList = new ArrayList<>();
        list.forEach(u->{
                 update(u,newList);
        });
    }

    public void update(FollowTraderEntity u, List<FollowTraderEntity> newList){
        QuoteClient quoteClient = null;
        if (u.getType().equals(TraderTypeEnum.MASTER_REAL.getType())){
            LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap()
                    .get(u.getId().toString());
            if (ObjectUtil.isEmpty(leaderApiTrader) || ObjectUtil.isEmpty(leaderApiTrader.quoteClient)
                    || !leaderApiTrader.quoteClient.Connected()) {
                leaderApiTradersAdmin.removeTrader(u.getId().toString());
                ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(u);
                if (conCodeEnum == ConCodeEnum.SUCCESS) {
                    quoteClient = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(u.getId().toString()).quoteClient;
                    leaderApiTrader= leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(u.getId().toString());
                    leaderApiTrader.startTrade();
                }else if (conCodeEnum == ConCodeEnum.AGAIN){
                    //重复提交
                    leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(u.getId().toString());
                    if (ObjectUtil.isNotEmpty(leaderApiTrader)){
                        quoteClient = leaderApiTrader.quoteClient;
                    }
                }
            } else {
                quoteClient = leaderApiTrader.quoteClient;
            }
        }else {
            CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap()
                    .get(u.getId().toString());
            if (ObjectUtil.isEmpty(copierApiTrader) || ObjectUtil.isEmpty(copierApiTrader.quoteClient)
                    || !copierApiTrader.quoteClient.Connected()) {
                copierApiTradersAdmin.removeTrader(u.getId().toString());
                ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(u);
                if (conCodeEnum == ConCodeEnum.SUCCESS) {
                    quoteClient = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(u.getId().toString()).quoteClient;
                    copierApiTrader= copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(u.getId().toString());
                    copierApiTrader.startTrade();
                }else if (conCodeEnum == ConCodeEnum.AGAIN){
                    //重复提交
                    copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(u.getId().toString());
                    if (ObjectUtil.isNotEmpty(copierApiTrader)){
                        quoteClient = copierApiTrader.quoteClient;
                    }
                }
            } else {
                quoteClient = copierApiTrader.quoteClient;
            }
        }
        //如果不等于空，获取历史数据
        if(ObjectUtil.isNotEmpty(quoteClient)){

            if (u.getIsFirstSync()!=null && u.getIsFirstSync()==CloseOrOpenEnum.OPEN.getValue()){
                //保存历史订单
              //    followOrderHistoryService.saveOrderHistory(quoteClient,u, DateUtil.toLocalDateTime(DateUtil.offsetDay(DateUtil.date(),-365)));
                //修改状态
                u.setIsFirstSync(CloseOrOpenEnum.CLOSE.getValue());
                //订单详情保存订单
                followOrderDetailService.saveOrderHistory(quoteClient,u, DateUtil.toLocalDateTime(DateUtil.offsetDay(DateUtil.date(),-365)));
                //保存持仓订单
                followOrderDetailService.saveOrderActive(quoteClient,u);
                newList.add(u);
            }else {
                //保存历史订单
                //    followOrderHistoryService.saveOrderHistory(quoteClient,u, DateUtil.toLocalDateTime(DateUtil.offsetDay(DateUtil.date(),-5)));
                //订单详情保存订单
                followOrderDetailService.saveOrderHistory(quoteClient,u, DateUtil.toLocalDateTime(DateUtil.offsetDay(DateUtil.date(),-1)));
                //保存持仓订单
                followOrderDetailService.saveOrderActive(quoteClient,u);
            }

        }
        if(ObjectUtil.isNotEmpty(newList)){
            followTraderService.updateBatchById(newList);
        }
    }


}
