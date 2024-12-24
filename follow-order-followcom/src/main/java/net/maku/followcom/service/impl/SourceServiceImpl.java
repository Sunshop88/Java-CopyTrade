package net.maku.followcom.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.convert.SourceConvert;
import net.maku.followcom.dao.SourceDao;
import net.maku.followcom.entity.SourceEntity;
import net.maku.followcom.service.SourceService;
import net.maku.followcom.vo.SourceInsertVO;
import net.maku.followcom.vo.SourceUpdateVO;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Author:  zsd
 * Date:  2024/11/14/周四 17:29
 * 喊单者
 */
@Service
@AllArgsConstructor
@Slf4j
@DS("slave")
public class SourceServiceImpl extends BaseServiceImpl<SourceDao, SourceEntity> implements SourceService {


    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void add(SourceInsertVO vo) {
        //参数转化
        SourceEntity sourceEntity = SourceConvert.INSTANCE.convert(vo);
        //暴力反射设置默认值
        Field[] declaredFields = sourceEntity.getClass().getDeclaredFields();
        Arrays.stream(declaredFields).forEach(x -> {
            x.setAccessible(true);
            try {
                Object o = x.get(sourceEntity);
                //如果为空，设置默认值
                if (ObjectUtil.isEmpty(o)) {
                    Class<?> type = x.getType();
                    if (type.equals(Integer.class)) {
                        x.set(sourceEntity, 0);
                    } else if (type.equals(Double.class)) {
                        x.set(sourceEntity, 0.00);
                    } else {
                        x.set(sourceEntity, null);
                    }

                }
            } catch (IllegalAccessException e) {
                throw new ServerException("喊单账号从表参数异常:" + e);
            }
        });
        //保存从表数据
        save(sourceEntity);

    }


    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void edit(SourceUpdateVO vo) {
        SourceEntity sourceEntity = SourceConvert.INSTANCE.convert(vo);
        //根据平台和账号进行编辑
        updateById(sourceEntity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void edit(  SourceEntity sourceEntity) {
        //根据平台和账号进行编辑
        updateById(sourceEntity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void del(Long id) {
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public SourceEntity getEntityById(Long id) {
        return getById(id);
    }
}
