package net.maku.subcontrol.dao;

import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.subcontrol.entity.FollowOrderHistoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 所有MT4账号的历史订单
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowOrderHistoryDao extends BaseDao<FollowOrderHistoryEntity> {

}