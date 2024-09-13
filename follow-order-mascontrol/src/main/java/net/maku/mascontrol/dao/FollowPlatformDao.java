package net.maku.mascontrol.dao;

import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.mascontrol.entity.FollowPlatformEntity;
import net.maku.mascontrol.vo.FollowPlatformVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
* 平台管理
*
* @author 阿沐 babamu@126.com
* @since 1.0.0 2024-09-11
*/
@Mapper
public interface FollowPlatformDao extends BaseDao<FollowPlatformEntity> {

    //根据id修改备注,时间
    @Update("update follow_platform set remark = #{remark}, update_time = #{updateTime},updater = #{updater} where id = #{id}")
    void updateByRemark(FollowPlatformEntity entity);
}