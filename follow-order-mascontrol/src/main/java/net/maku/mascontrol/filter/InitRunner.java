package net.maku.mascontrol.filter;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowVersionEntity;
import net.maku.followcom.service.FollowVersionService;
import net.maku.followcom.util.FollowConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(1)
@Slf4j
public class InitRunner implements ApplicationRunner {
    @Autowired
    private FollowVersionService followVersionService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=============启动时加载示例内容开始=============");
        log.info("版本更新=======开始");
        setVersion();
        log.info("版本更新=======结束");
        log.info("全局加密=======开始");
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
}
