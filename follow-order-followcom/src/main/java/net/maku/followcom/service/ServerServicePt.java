package net.maku.followcom.service;


import net.maku.followcom.entity.ServerEntity;
import net.maku.framework.mybatis.service.BaseService;

import java.util.List;

/**
 * 外部服务器
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface ServerServicePt extends BaseService<ServerEntity> {


    void delete(List<Long> idList);

    void insert(ServerEntity serverEntity);
}