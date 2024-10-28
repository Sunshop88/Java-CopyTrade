package net.maku.subcontrol.trader;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.util.FollowConstant;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

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

    @Autowired
    private FollowBrokeServerService followBrokeServerService;

    @Autowired
    private RedisCache redisCache;
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=============启动时加载示例内容开始=============");
        // 连接MT4交易账户
        mt4TraderStartup();
        log.info("=============启动时加载示例内容完毕=============");
    }


    private void mt4TraderStartup() throws Exception {
        log.info("当前ip"+FollowConstant.LOCAL_HOST);
        //删除所有进行中的下单和平常redis
        redisCache.deleteByPattern(Constant.TRADER_SEND);
        redisCache.deleteByPattern(Constant.TRADER_CLOSE);
        // 2.启动喊单者和跟单者
        // mt4账户 喊单
        List<FollowTraderEntity> mt4TraderList = aotfxTraderService.list(Wrappers.<FollowTraderEntity>lambdaQuery()
                .eq(FollowTraderEntity::getIpAddr, FollowConstant.LOCAL_HOST)
                .in(FollowTraderEntity::getType, TraderTypeEnum.MASTER_REAL.getType())
                .eq(FollowTraderEntity::getDeleted, CloseOrOpenEnum.CLOSE.getValue())
                .orderByAsc(FollowTraderEntity::getCreateTime));
        // 分类MASTER和SLAVE
        long masters = mt4TraderList.stream().filter(o->o.getType().equals(TraderTypeEnum.MASTER_REAL.getType())).count();
        log.info("===============喊单者{}", masters);
        leaderApiTradersAdmin.startUp();
        // mt4账户 跟单
        // 分类MASTER和SLAVE
        long slave = mt4TraderList.stream().filter(o->o.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())).count();
        log.info("===============跟单者{}", slave);
        copierApiTradersAdmin.startUp();
    }
}
