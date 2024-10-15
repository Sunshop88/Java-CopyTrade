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
    private FollowTraderService aotfxTraderService;

    @Autowired
    FollowTraderSubscribeService masterSlaveService;

    @Autowired
    private FollowBrokeServerService followBrokeServerService;

    @Autowired
    private FollowPlatformService followPlatformService;
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=============启动时加载示例内容开始=============");
        // 连接MT4交易账户
        mt4TraderStartup();
        log.info("=============启动时加载示例内容完毕=============");
    }


    private void mt4TraderStartup() throws Exception {
        // 2.启动喊单者和跟单者
        // mt4账户
        List<FollowTraderEntity> mt4TraderList = aotfxTraderService.list(Wrappers.<FollowTraderEntity>lambdaQuery()
                .eq(FollowTraderEntity::getIpAddr, FollowConstant.LOCAL_HOST)
                .in(FollowTraderEntity::getType, TraderTypeEnum.MASTER_REAL.getType())
                .eq(FollowTraderEntity::getDeleted, CloseOrOpenEnum.CLOSE.getValue())
                .orderByAsc(FollowTraderEntity::getCreateTime));
        // 分类MASTER和SLAVE
        long masters = mt4TraderList.stream().filter(o->o.getType().equals(TraderTypeEnum.MASTER_REAL.getType())).count();
        log.info("===============喊单者{}", masters);
        leaderApiTradersAdmin.startUp();

        //重新测速已有账号平台
        List<FollowBrokeServerEntity> list = followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>().in(FollowBrokeServerEntity::getServerName,followPlatformService.list().stream().map(FollowPlatformEntity::getServer).collect(Collectors.toList())));
        //进行测速
        list.forEach(o->{
            String ipAddress = o.getServerNode(); // 目标IP地址
            int port = Integer.valueOf(o.getServerPort()); // 目标端口号
            try {
                AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
                long startTime = System.currentTimeMillis(); // 记录起始时间
                Future<Void> future = socketChannel.connect(new InetSocketAddress(ipAddress, port));
                // 等待连接完成
                future.get();
                long endTime = System.currentTimeMillis(); // 记录结束时间
                o.setSpeed((int)endTime - (int)startTime);
                followBrokeServerService.updateById(o);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        list.stream().map(FollowBrokeServerEntity::getServerName).distinct().forEach(o->{
            //找出最小延迟
            FollowBrokeServerEntity followBrokeServer = followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName, o).orderByAsc(FollowBrokeServerEntity::getSpeed)).get(0);
            //修改所有用户连接节点
            followPlatformService.update(Wrappers.<FollowPlatformEntity>lambdaUpdate().eq(FollowPlatformEntity::getServer,followBrokeServer.getServerName()).set(FollowPlatformEntity::getServerNode,followBrokeServer.getServerNode()+":"+followBrokeServer.getServerPort()));
        });

    }
}
