package net.maku.followcom.service.impl;


import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.dao.ServerDao;
import net.maku.followcom.entity.PlatformEntity;
import net.maku.followcom.entity.ServerEntity;
import net.maku.followcom.service.ServerService;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 外部服务器
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
@Slf4j
@DS("slave")
public class ServerServiceImpl extends BaseServiceImpl<ServerDao, ServerEntity> implements ServerService {

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    @Override
    public void delete(List<Long> idList) {
        for (Long platformId : idList) {
            //根据platformId删除
            Wrapper<ServerEntity> querWrapper = Wrappers.<ServerEntity>lambdaQuery()
                    .eq(ServerEntity::getPlatformId, platformId);
            baseMapper.delete(querWrapper);
        }
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    @Override
    public void insert(ServerEntity serverEntity) {
        baseMapper.insert(serverEntity);
    }
}