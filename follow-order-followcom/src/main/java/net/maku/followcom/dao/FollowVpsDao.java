package net.maku.followcom.dao;

import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.followcom.entity.FollowVpsEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * vps列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowVpsDao extends BaseDao<FollowVpsEntity> {

    void updateVps(FollowVpsEntity entity);
}