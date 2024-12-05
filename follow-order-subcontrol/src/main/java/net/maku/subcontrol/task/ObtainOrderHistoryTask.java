package net.maku.subcontrol.task;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.utils.Result;
import net.maku.subcontrol.service.FollowOrderHistoryService;
import net.maku.subcontrol.trader.LeaderApiTrader;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import online.mtapi.mt4.QuoteClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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


    @Scheduled(cron = "0 0 23 ? * *")
    public void getOrderHistory(){
        //1.获取所有账号
        List<FollowTraderEntity> list = followTraderService.list();
        long start = System.currentTimeMillis();
        //获取mt4客户端quoteClient
        list.forEach(u->{
            LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap()
                    .get(u.getId().toString());
            QuoteClient quoteClient = null;
            //mt4登录
            if (ObjectUtil.isEmpty(leaderApiTrader) || ObjectUtil.isEmpty(leaderApiTrader.quoteClient)
                    || !leaderApiTrader.quoteClient.Connected()) {
                ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(u);
                if (conCodeEnum == ConCodeEnum.SUCCESS) {
                    quoteClient = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(u.getId().toString()).quoteClient;
                    LeaderApiTrader leaderApiTrader1 = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(u.getId().toString());
                    leaderApiTrader1.startTrade();
                }
            } else {
                quoteClient = leaderApiTrader.quoteClient;
            }

            //如果不等于空，获取历史数据
            if(ObjectUtil.isNotEmpty(quoteClient)){
                //保存历史订单
                followOrderHistoryService.saveOrderHistory(quoteClient,u, DateUtil.toLocalDateTime(DateUtil.offsetDay(DateUtil.date(),-365)));
            }


        });
        long end = System.currentTimeMillis();
        System.out.println("定时执行完毕----------------》"+(end-start));
    }
}
