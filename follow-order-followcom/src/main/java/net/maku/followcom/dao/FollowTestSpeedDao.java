package net.maku.followcom.dao;

import net.maku.followcom.entity.FollowTestSpeedEntity;
import net.maku.followcom.vo.FollowTestSpeedVO;
import net.maku.framework.mybatis.dao.BaseDao;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测速记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowTestSpeedDao extends BaseDao<FollowTestSpeedEntity> {

    void saveTestSpeed(FollowTestSpeedVO overallResult);

}