package net.maku.followcom.dao;

import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.followcom.entity.FollowOrderCloseEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 平仓记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowOrderCloseDao extends BaseDao<FollowOrderCloseEntity> {

}