package net.maku.subcontrol.task;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.VpsSpendEnum;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.security.user.SecurityUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SpeedTestTask {
    @Autowired
    private FollowPlatformService followPlatformService;
    @Autowired
    private FollowVpsService followVpsService;
    @Autowired
    private FollowTestSpeedService followTestSpeedService;
    @Autowired
    private FollowTestDetailService followTestDetailService;
    @Autowired
    private RedisUtil redisUtil;

    @Scheduled(cron = "0 0 0 ? * MON")
    //    @Scheduled(cron = "*/60 * * * * ?")//测试
    public void weeklySpeedTest() throws IOException {
        log.info("开始执行每周测速任务...");
        try {
            // 从数据库中获取所有需要测速的服务器
            List<String> servers = followPlatformService.listByServer()
                    .stream()
                    .filter(Objects::nonNull)  // 过滤掉 null 值
                    .map(FollowPlatformVO::getServer)
                    //                    .filter(Objects::nonNull)  // 再次过滤掉可能的 null 值
                    .collect(Collectors.toList());
            //查看是哪个ip
            String ip = FollowConstant.LOCAL_HOST;
            FollowVpsEntity vpsEntity = followVpsService.getVps(ip);

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

            boolean isSuccess = followTestSpeedService.measure(servers, vpsEntity, overallResult.getId());
            if (isSuccess) {
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
                            //查询vps名称所对应的id
                            Integer vpsId = followVpsService.getOne(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getName, vpsName)).getId();
                            redisUtil.hset(Constant.VPS_NODE_SPEED + vpsId, serverName, minLatencyEntity.getServerNode(), 0);
                        }
                    });
                });
            } else {
                // 删除当前vps相关的数据
                followTestDetailService.deleteByTestId(overallResult.getId());

                overallResult.setStatus(VpsSpendEnum.FAILURE.getType());
                // 延迟删除操作，确保在所有测速请求完成后再进行删除
                followTestDetailService.deleteByTestId(overallResult.getId());
            }
            followTestSpeedService.update(overallResult);
            log.info("每周测速任务执行完成...");

        } catch (Exception e) {
            log.error("每周测速任务执行异常: ", e);
        }
    }
}
