package net.maku.followcom.dao;

import net.maku.followcom.vo.FollowTraderCountVO;
import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.followcom.entity.FollowPlatformEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.util.List;

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

    List<FollowTraderCountVO> getBrokerNames();
}