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


}