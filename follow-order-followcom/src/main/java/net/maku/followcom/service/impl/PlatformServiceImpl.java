package net.maku.followcom.service.impl;


import cn.hutool.core.util.ObjectUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.dao.PlatformDao;
import net.maku.followcom.entity.PlatformEntity;
import net.maku.followcom.service.PlatformService;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 外部平台
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
@Slf4j
@DS("slave")
public class PlatformServiceImpl extends BaseServiceImpl<PlatformDao, PlatformEntity> implements PlatformService {

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    @Override
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    @Override
    public void insert(PlatformEntity platformEntity) {
        if (ObjectUtil.isNotEmpty(baseMapper.selectOne(Wrappers.<PlatformEntity>lambdaQuery().eq(PlatformEntity::getName, platformEntity.getName())))) {
            throw new ServerException("名称重复，请重新输入");
        }
        baseMapper.insert(platformEntity);

    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    @Override
    public void update(UpdateWrapper<PlatformEntity> platformEntity) {
        baseMapper.update(platformEntity);

    }


}