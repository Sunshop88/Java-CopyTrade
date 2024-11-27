package net.maku.quartz.task;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowTestDetailEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.enums.VpsSpendEnum;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.vo.FollowPlatformVO;
import net.maku.followcom.vo.FollowTestSpeedVO;
import net.maku.followcom.vo.FollowVpsVO;
import net.maku.followcom.vo.MeasureRequestVO;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.security.user.SecurityUser;
import net.maku.system.service.SysUserTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.AsynchronousSocketChannel;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    @Autowired
    private FollowTestDetailService followTestDetailService;
    @Autowired
    private FollowTestSpeedService followTestSpeedService;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private SysUserTokenService sysUserTokenService;


    public void run(String param) throws InterruptedException {
        log.info("开始执行节点测速任务");
        //重新测速已有账号平台
        List<FollowBrokeServerEntity> list = followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>().in(FollowBrokeServerEntity::getServerName, followPlatformService.list().stream().map(FollowPlatformEntity::getServer).collect(Collectors.toList())));
        //进行测速
        list.parallelStream().forEach(o -> {
            String ipAddress = o.getServerNode(); // 目标IP地址
            int port = Integer.valueOf(o.getServerPort()); // 目标端口号
            try {
                AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
                long startTime = System.currentTimeMillis(); // 记录起始时间
                Future<Void> future = socketChannel.connect(new InetSocketAddress(ipAddress, port));
                // 等待连接完成
                future.get();
                long endTime = System.currentTimeMillis(); // 记录结束时间
                o.setSpeed((int) endTime - (int) startTime);
                followBrokeServerService.updateById(o);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        list.stream().map(FollowBrokeServerEntity::getServerName).distinct().forEach(o -> {
            //找出最小延迟
            FollowBrokeServerEntity followBrokeServer = followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName, o).orderByAsc(FollowBrokeServerEntity::getSpeed)).get(0);
            //修改所有用户连接节点
            followPlatformService.update(Wrappers.<FollowPlatformEntity>lambdaUpdate().eq(FollowPlatformEntity::getServer, followBrokeServer.getServerName()).set(FollowPlatformEntity::getServerNode, followBrokeServer.getServerNode() + ":" + followBrokeServer.getServerPort()));
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

    @Scheduled(cron = "0 0 0 ? * MON") // 每周一凌晨 0 点触发任务
//    @Scheduled(cron = "*/30 * * * * ?")//三十秒测一次
    public void weeklySpeedTest() {
        try {
            log.info("开始执行每周测速任务...");

            // 从数据库中获取所有需要测速的服务器和 VPS 列表
            List<String> servers = followPlatformService.listByServer()
                    .stream()
                    .filter(Objects::nonNull)  // 过滤掉 null 值
                    .map(FollowPlatformVO::getServer)
//                    .filter(Objects::nonNull)  // 再次过滤掉可能的 null 值
                    .collect(Collectors.toList());
            List<String> vps = followVpsService.listByVps()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(FollowVpsVO::getName)
//                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (ObjectUtil.isEmpty(servers) || ObjectUtil.isEmpty(vps)) {
                log.warn("服务器列表或 VPS 列表为空，跳过测速任务");
                return;
            }

            // 调用现有测速逻辑
            FollowTestSpeedVO overallResult = new FollowTestSpeedVO();
            overallResult.setStatus(VpsSpendEnum.IN_PROGRESS.getType());
            overallResult.setDoTime(new Date());
            overallResult.setVersion(0);
            overallResult.setDeleted(0);
            overallResult.setCreator(SecurityUser.getUserId());
            overallResult.setCreateTime(LocalDateTime.now());
            overallResult.setTestName("系统测速System");
            // 保存到数据库
            followTestSpeedService.saveTestSpeed(overallResult);

            // 执行测速任务
            extracted(vps, servers, overallResult);

            log.info("每周测速任务执行完成");
        } catch (Exception e) {
            log.error("每周测速任务执行异常: ", e);
        }
    }

    private void extracted(List<String> vps, List<String> servers, FollowTestSpeedVO overallResult) throws JsonProcessingException {
        List<FollowVpsEntity> vpsList = followVpsService.listByVpsName(vps);
        ObjectMapper objectMapper = new ObjectMapper();
        boolean allSuccess = true;

        ExecutorService executorService = Executors.newFixedThreadPool(10); // 创建固定大小的线程池
        List<Future<Boolean>> futures = new ArrayList<>(); // 存储每个任务的 Future 对象

        for (FollowVpsEntity vpsEntity : vpsList) {
            // 平台点击测速的时候，断开连接的VPS不需要发起测速请求
            try {
                InetAddress inet = InetAddress.getByName(vpsEntity.getIpAddress());
                boolean reachable = inet.isReachable(5000);
                if (!reachable) {
                    log.warn("VPS 地址不可达: " + vpsEntity.getIpAddress() + ", 跳过该VPS");
                    continue;
                }
                try (Socket socket = new Socket(vpsEntity.getIpAddress(), Integer.parseInt(FollowConstant.VPS_PORT))) {
                    log.info("成功连接到 VPS: " + vpsEntity.getIpAddress());
                } catch (IOException e) {
                    log.warn("VPS 服务未启动: " + vpsEntity.getIpAddress() + ", 跳过该VPS");
                    continue;
                }
            } catch (IOException e) {
                log.error("请求异常: " + e.getMessage() + ", 跳过该VPS");
                continue;
            }

            futures.add(executorService.submit(() -> {
                String url = MessageFormat.format("http://{0}:{1}{2}", vpsEntity.getIpAddress(), FollowConstant.VPS_PORT, FollowConstant.VPS_MEASURE);

                MeasureRequestVO startRequest = new MeasureRequestVO();
                startRequest.setServers(servers);
                startRequest.setVpsEntity(vpsEntity);
                startRequest.setTestId(overallResult.getId());

                // 手动序列化 FollowVpsEntity 中的 expiryDate 字段
                String expiryDateStr = vpsEntity.getExpiryDate().toString();
                startRequest.setExpiryDateStr(expiryDateStr);

                RestTemplate restTemplate = new RestTemplate();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                String token = sysUserTokenService.getById(1).getAccessToken();
                headers.set("Authorization", token);

                HttpEntity<MeasureRequestVO> entity = new HttpEntity<>(startRequest, headers);
                ResponseEntity<JSONObject> response = restTemplate.exchange(url, HttpMethod.POST, entity, JSONObject.class);
                log.info("测速请求: " + response.getBody());

                if (!response.getBody().getString("msg").equals("success")) {
                    log.error("测速失败ip: " + vpsEntity.getIpAddress() + ", 响应: " + response.getBody());
                    return false; // 返回失败状态
                }
                return true; // 返回成功状态
            }));
        }
        // 等待所有任务完成并检查结果
        for (Future<Boolean> future : futures) {
            try {
                if (!future.get()) { // 如果有任何任务返回失败
                    allSuccess = false;
                    break;
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("测速任务执行异常: " + e.getMessage());
                allSuccess = false;
                break;
            }
        }
        // 根据所有任务的执行结果更新 overallResult
        if (allSuccess) {
            overallResult.setStatus(VpsSpendEnum.SUCCESS.getType());

            List<FollowTestDetailEntity> allEntities = followTestDetailService.list(
                    new LambdaQueryWrapper<FollowTestDetailEntity>()
                            .eq(FollowTestDetailEntity::getTestId, overallResult.getId())
            );
            // 获取所有唯一的 VPS 名称
            List<String> vpsNames = allEntities.stream()
                    .map(FollowTestDetailEntity::getVpsName)
                    .distinct()
                    .collect(Collectors.toList());

            vpsNames.forEach(vpsName -> {
                // 获取当前 VPS 名称下的所有服务器名称
                List<String> serverNames = allEntities.stream()
                        .filter(entity -> vpsName.equals(entity.getVpsName()))
                        .map(FollowTestDetailEntity::getServerName)
                        .distinct()
                        .collect(Collectors.toList());
                serverNames.forEach(serverName -> {
                    // 查找当前 VPS 名称和服务器名称下的最小延迟
                    FollowTestDetailEntity minLatencyEntity = allEntities.stream()
                            .filter(entity -> vpsName.equals(entity.getVpsName()) && serverName.equals(entity.getServerName()))
                            .min(Comparator.comparingLong(FollowTestDetailEntity::getSpeed))
                            .orElse(null);

                    if (ObjectUtil.isNotEmpty(minLatencyEntity)) {
                        // 查询 vps 名称所对应的 id
                        Integer vpsId = followVpsService.getOne(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getName, vpsName)).getId();
                        redisUtil.hset(Constant.VPS_NODE_SPEED + vpsId, serverName, minLatencyEntity.getServerNode(), 0);
                    }
                });
            });
        } else {
            overallResult.setStatus(VpsSpendEnum.FAILURE.getType());
            // 延迟删除操作，确保在所有测速请求完成后再进行删除
            followTestDetailService.deleteByTestId(overallResult.getId());
        }

        followTestSpeedService.update(overallResult);
        executorService.shutdown(); // 关闭线程池
    }

}
