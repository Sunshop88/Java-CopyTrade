package net.maku.followcom.service;


import net.maku.followcom.entity.PlatformEntity;
import net.maku.framework.mybatis.service.BaseService;

import java.util.List;

/**
 * 外部平台
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface PlatformService extends BaseService<PlatformEntity> {


    void delete(List<Long> idList);
}