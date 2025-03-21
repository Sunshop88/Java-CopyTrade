package net.maku.mascontrol;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowVersionEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.service.FollowVersionService;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.ServersDatIniUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Order(1)
@Slf4j
public class InitRunner implements ApplicationRunner {
    @Autowired
    private FollowVersionService followVersionService;
    @Autowired
    private FollowVpsService followVpsService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=============启动时加载示例内容开始=============");
        log.info("版本更新=======开始");
        setVersion();
        log.info("版本更新=======结束");
        log.info("全局加密=======开始");
        // 启动定时任务，每5秒执行一次
        startScheduledTask();
    }

    private void setVersion() {
        String ip = FollowConstant.LOCAL_HOST;
        //获取最新版本
        String version = FollowConstant.MAS_VERSION;
        //获取当前版本
        List<FollowVersionEntity> entities = followVersionService.list(new LambdaQueryWrapper<FollowVersionEntity>().eq(FollowVersionEntity::getIp, ip + "主").eq(FollowVersionEntity::getVersions, version));
        if (ObjectUtil.isEmpty(entities)) {
            FollowVersionEntity entity = new FollowVersionEntity();
            entity.setIp(ip + "主");
            entity.setVersions(version);
            followVersionService.save(entity);
        }
    }
    private void startScheduledTask() {
        // 定时任务，每5秒执行一次
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<FollowVpsEntity> list = followVpsService.list(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getDeleted, CloseOrOpenEnum.CLOSE.getValue()));
                list.forEach(o->{
                    boolean connected = ServersDatIniUtil.connected(o.getIpAddress(), Integer.parseInt(FollowConstant.VPS_PORT));
                    followVpsService.update(new LambdaUpdateWrapper<FollowVpsEntity>().set(FollowVpsEntity::getIsStop, connected?CloseOrOpenEnum.CLOSE.getValue():CloseOrOpenEnum.OPEN.getValue()).eq(FollowVpsEntity::getIpAddress, FollowConstant.LOCAL_HOST));
                });
            } catch (Exception e) {
                log.error("定时任务执行出错", e);
            }
        }, 0, 5, TimeUnit.SECONDS); // 初始延迟0秒，每5秒执行一次
    }
}
