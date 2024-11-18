package net.maku.followcom.service.impl;


import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.dao.PlatformDao;
import net.maku.followcom.entity.PlatformEntity;
import net.maku.followcom.service.PlatformService;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import org.springframework.stereotype.Service;

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


    @Override
    public void delete(List<Long> idList) {
        removeByIds(idList);

        }
}