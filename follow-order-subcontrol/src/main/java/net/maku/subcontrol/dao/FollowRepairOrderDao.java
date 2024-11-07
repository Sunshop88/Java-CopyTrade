package net.maku.subcontrol.dao;

import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.subcontrol.entity.FollowRepairOrderEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 补单记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowRepairOrderDao extends BaseDao<FollowRepairOrderEntity> {

}