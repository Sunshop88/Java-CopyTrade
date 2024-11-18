package net.maku.followcom.dao;

import net.maku.followcom.entity.ServerEntity;
import net.maku.framework.mybatis.dao.BaseDao;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部服务器
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface ServerDao extends BaseDao<ServerEntity> {

}