package net.maku.followcom.dao;

import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.followcom.entity.FollowOrderActiveEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账号持仓订单
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowOrderActiveDao extends BaseDao<FollowOrderActiveEntity> {

}