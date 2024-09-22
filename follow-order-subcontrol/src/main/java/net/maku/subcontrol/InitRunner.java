package net.maku.subcontrol;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderStatusEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.subcontrol.constants.FollowConstant;
import net.maku.subcontrol.trader.CopierApiTradersAdmin;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Shaozz
 * @since 2021/7/6 10:35
 */
@Component
@Order(1)
@Slf4j
public class InitRunner implements ApplicationRunner {


    @Autowired
    private LeaderApiTradersAdmin leaderApiTradersAdmin;

    @Autowired
    private CopierApiTradersAdmin copierApiTradersAdmin;

    @Autowired
    private FollowTraderService aotfxTraderService;

    @Autowired
    FollowTraderSubscribeService masterSlaveService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=============启动时加载示例内容开始=============");

        // 连接MT4交易账户
//        mt4TraderStartup();

        // 币种价格推送
//        getS

        log.info("=============启动时加载示例内容完毕=============");
    }


    private void mt4TraderStartup() throws Exception {
        // 2.启动喊单者和跟单者
        // mt4账户
        List<FollowTraderEntity> mt4TraderList = aotfxTraderService.list(Wrappers.<FollowTraderEntity>lambdaQuery()
                .eq(FollowTraderEntity::getStatus, TraderStatusEnum.NORMAL.getValue())
                .eq(FollowTraderEntity::getIpAddr,FollowConstant.LOCAL_HOST)
                .in(FollowTraderEntity::getType, TraderTypeEnum.MASTER_REAL.getType(), TraderTypeEnum.SLAVE_REAL.getType())
                .eq(FollowTraderEntity::getDeleted, CloseOrOpenEnum.CLOSE.getValue())
                .orderByAsc(FollowTraderEntity::getCreateTime));
        // 分类MASTER和SLAVE
        long masters = mt4TraderList.stream().filter(o->o.getType().equals(TraderTypeEnum.MASTER_REAL.getType())).count();
        long salves = mt4TraderList.stream().filter(o->o.getType().equals(TraderTypeEnum.MASTER_REAL.getType())).count();

        log.info("===============喊单者{}，跟单者{}===================", masters, salves);
        leaderApiTradersAdmin.startUp();
        copierApiTradersAdmin.startUp();
    }
}
