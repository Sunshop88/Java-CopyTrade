package net.maku.subcontrol.task;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.convert.FollowTestSpeedConvert;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.*;
import net.maku.followcom.service.*;
import net.maku.followcom.service.impl.FollowBrokeServerServiceImpl;
import net.maku.followcom.util.AesUtils;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.security.user.SecurityUser;
import net.maku.subcontrol.trader.CopierApiTrader;
import net.maku.subcontrol.trader.CopierApiTradersAdmin;
import net.maku.subcontrol.trader.LeaderApiTrader;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
//@Service
@Component
public class SpeedTestTask {
    @Autowired
    private FollowBrokeServerService followBrokeServerService;
    @Autowired
    private FollowPlatformService followPlatformService;
    @Autowired
    private FollowVpsService followVpsService;
    @Autowired
    private FollowTestSpeedService followTestSpeedService;
    @Autowired
    private FollowTestDetailService followTestDetailService;
    @Autowired
    private FollowSpeedSettingService followSpeedSettingService;
    @Autowired
    private FollowTraderService followTraderService;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private LeaderApiTradersAdmin leaderApiTradersAdmin;
    @Autowired
    private CopierApiTradersAdmin copierApiTradersAdmin;

    @Scheduled(cron = "0 0 14 ? * SAT")
    //    @Scheduled(cron = "*/60 * * * * ?")//测试
    public void weeklySpeedTest() throws IOException {
        log.info("开始执行每周测速任务...");
        try {
            // 从数据库中获取所有需要测速的服务器
            List<String> servers = followBrokeServerService.listByServer()
                    .stream()
                    .filter(Objects::nonNull)  // 过滤掉 null 值
                    .map(FollowBrokeServerVO::getServerName)
                    //                    .filter(Objects::nonNull)  // 再次过滤掉可能的 null 值
                    .collect(Collectors.toList());
            //查看是哪个ip
//            String ip = "192.168.31.40";
            String ip = FollowConstant.LOCAL_HOST;
            FollowVpsEntity vpsEntity = followVpsService.getVps(ip);
            log.warn( "当前内容为：" +vpsEntity);

            // 调用现有测速逻辑
            FollowTestSpeedVO overallResult = new FollowTestSpeedVO();
            overallResult.setStatus(VpsSpendEnum.IN_PROGRESS.getType());
//            overallResult.setDoTime(new Date());
            overallResult.setDoTime(LocalDateTime.now());
            overallResult.setVersion(0);
            overallResult.setDeleted(0);
            overallResult.setCreator(SecurityUser.getUserId());
            overallResult.setCreateTime(LocalDateTime.now());
            overallResult.setTestName("系统测速System");
            // 保存到数据库
            followTestSpeedService.saveTestSpeed(overallResult);

            FollowSpeedSettingEntity settingEntity = followSpeedSettingService.getById(1);

            boolean isSuccess = followTestSpeedService.measureTask(servers, vpsEntity, overallResult.getId(),overallResult.getDoTime());
            if (isSuccess) {
                overallResult.setStatus(VpsSpendEnum.SUCCESS.getType());
                if (settingEntity.getDefaultServerNode() == 0) {

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
                                    .min(Comparator.comparingLong(entity -> entity.getSpeed() != null ? entity.getSpeed() : Long.MAX_VALUE))
                                    .orElse(null);

                            if (ObjectUtil.isNotEmpty(minLatencyEntity) && minLatencyEntity.getSpeed() != null) {
                                //将最快的节点后面加上isDefaultServerNode =0
                                minLatencyEntity.setIsDefaultServer(0);
                                followTestDetailService.updateById(minLatencyEntity);
                                //查询vps名称所对应的id
                                Integer vpsId = followVpsService.getOne(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getName, vpsName).eq(FollowVpsEntity::getDeleted, 0)).getId();
                                redisUtil.hset(Constant.VPS_NODE_SPEED + vpsId, serverName, minLatencyEntity.getServerNode(), 0);
                            }
                        });
                    });
                }
            } else {
                // 删除当前vps相关的数据
                followTestDetailService.deleteByTestId(overallResult.getId());

                overallResult.setStatus(VpsSpendEnum.FAILURE.getType());
                // 延迟删除操作，确保在所有测速请求完成后再进行删除
                followTestDetailService.deleteByTestId(overallResult.getId());
            }
            followTestSpeedService.update(overallResult);
            log.info("每周测速任务执行完成...");

            if (settingEntity.getDefaultServerNodeLogin() == 0) {
                LambdaQueryWrapper<FollowTraderEntity> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(FollowTraderEntity::getStatus, CloseOrOpenEnum.CLOSE.getValue());
                List<FollowTraderEntity> list = followTraderService.list(wrapper);
                Map<String, Boolean> reconnectResults = new HashMap<>();

                for (FollowTraderEntity followTraderEntity : list) {
                    String traderId = followTraderEntity.getId().toString();
                    Boolean reconnect = reconnect(traderId);
                    reconnectResults.put(traderId, reconnect);
                }
                log.info("连接结果: {}", reconnectResults.toString());
            }

        } catch (Exception e) {
            //查询最新的测速记录，上面followTestSpeedService.saveTestSpeed(overallResult);新增的数据
            FollowTestSpeedEntity latestRecord = followTestSpeedService.getOne(
                    new LambdaQueryWrapper<FollowTestSpeedEntity>()
                            .orderByDesc(FollowTestSpeedEntity::getId)
                            .last("limit 1")
            );
            if (ObjectUtil.isNotEmpty(latestRecord)) {
                latestRecord.setStatus(VpsSpendEnum.FAILURE.getType());
                //转成VO
                FollowTestSpeedVO followTestSpeedVO = FollowTestSpeedConvert.INSTANCE.convert(latestRecord);
                followTestSpeedService.update(followTestSpeedVO);
            }

            log.error("每周测速任务执行异常: ", e);
        }
    }

    private Boolean reconnect(String traderId) {
        Boolean result=false;
        try {
            FollowTraderEntity followTraderEntity = followTraderService.getById(traderId);
            if (followTraderEntity.getType().equals(TraderTypeEnum.MASTER_REAL.getType())){
                leaderApiTradersAdmin.removeTrader(traderId);
                ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderService.getById(traderId));
                if (conCodeEnum != ConCodeEnum.SUCCESS&&conCodeEnum != ConCodeEnum.AGAIN) {
                    followTraderEntity.setStatus(TraderStatusEnum.ERROR.getValue());
                    followTraderService.updateById(followTraderEntity);
                    log.error("喊单者:[{}-{}-{}]重连失败，请校验", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName());
                    throw new ServerException("重连失败");
                }  else if (conCodeEnum == ConCodeEnum.AGAIN){
                    log.info("喊单者:[{}-{}-{}]启动重复", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName());
                } else {
                    LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId);
                    log.info("喊单者:[{}-{}-{}-{}]在[{}:{}]重连成功", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName(), AesUtils.decryptStr(followTraderEntity.getPassword()), leaderApiTrader.quoteClient.Host, leaderApiTrader.quoteClient.Port);
                    leaderApiTrader.startTrade();
                    result=true;
                }
            }else {
                copierApiTradersAdmin.removeTrader(traderId);
                ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderService.getById(traderId));
                if (conCodeEnum != ConCodeEnum.SUCCESS&&conCodeEnum != ConCodeEnum.AGAIN) {
                    followTraderEntity.setStatus(TraderStatusEnum.ERROR.getValue());
                    followTraderService.updateById(followTraderEntity);
                    log.error("跟单者:[{}-{}-{}]重连失败，请校验", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName());
                    throw new ServerException("重连失败");
                } else if (conCodeEnum == ConCodeEnum.AGAIN){
                    log.info("跟单者:[{}-{}-{}]启动重复", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName());
                }  else {
                    CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(traderId);
                    log.info("跟单者:[{}-{}-{}-{}]在[{}:{}]重连成功", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName(), AesUtils.decryptStr(followTraderEntity.getPassword()), copierApiTrader.quoteClient.Host, copierApiTrader.quoteClient.Port);
                    copierApiTrader.startTrade();
                    result=true;
                }
            }
        }catch (Exception e){
            result=false;
        }
        return result;
    }
}
