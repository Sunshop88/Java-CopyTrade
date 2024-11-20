package net.maku.quartz.task;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.util.FollowConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * 节点测速定时任务
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Slf4j
@Service
public class BrokeServerSpeedTask {
    @Autowired
    private FollowBrokeServerService followBrokeServerService;
    @Autowired
    private FollowPlatformService followPlatformService;
    @Autowired
    private FollowVpsService followVpsService;


    public void run(String param) throws InterruptedException {
        log.info("开始执行节点测速任务");
        //重新测速已有账号平台
        List<FollowBrokeServerEntity> list = followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>().in(FollowBrokeServerEntity::getServerName,followPlatformService.list().stream().map(FollowPlatformEntity::getServer).collect(Collectors.toList())));
        //进行测速
        list.parallelStream().forEach(o->{
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

    public void connect(String param) throws InterruptedException {
        // 需要定时检测VPS连接状态，一个小时检测1次
        log.info("开始执行VPS连接状态检查");
        // 查询所有VPS列表
        List<FollowVpsEntity> list = followVpsService.list();
        list.parallelStream().forEach(vpsEntity -> {
            try {
                InetAddress inet = InetAddress.getByName(vpsEntity.getIpAddress());
                boolean reachable = inet.isReachable(5000);
                if (!reachable) {
                    log.warn("VPS 地址不可达: " + vpsEntity.getIpAddress() + ", 跳过该VPS");
                    vpsEntity.setConnectionStatus(0);
                    followVpsService.updateStatus(vpsEntity);
                    return;
                }
                try (Socket socket = new Socket(vpsEntity.getIpAddress(), Integer.parseInt(FollowConstant.VPS_PORT))) {
                    log.info("成功连接到 VPS: " + vpsEntity.getIpAddress());
                    vpsEntity.setConnectionStatus(1);
                    followVpsService.updateStatus(vpsEntity);
                } catch (IOException e) {
                    log.warn("VPS 服务未启动: " + vpsEntity.getIpAddress() + ", 跳过该VPS");
                    vpsEntity.setConnectionStatus(0);
                    followVpsService.updateStatus(vpsEntity);
                }
            } catch (IOException e) {
                log.error("请求异常: " + e.getMessage() + ", 跳过该VPS");
                vpsEntity.setConnectionStatus(0);
                followVpsService.updateStatus(vpsEntity);
            }
        });
    }

}
