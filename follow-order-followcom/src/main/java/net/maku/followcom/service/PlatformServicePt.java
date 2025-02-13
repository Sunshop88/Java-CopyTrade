package net.maku.followcom.service;


import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import net.maku.followcom.entity.PlatformEntity;
import net.maku.framework.mybatis.service.BaseService;

import java.util.List;

/**
 * 外部平台
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface PlatformServicePt extends BaseService<PlatformEntity> {


    void delete(List<Long> idList);

    void insert(PlatformEntity platformEntity);

    void update(UpdateWrapper<PlatformEntity> platformEntity);
}