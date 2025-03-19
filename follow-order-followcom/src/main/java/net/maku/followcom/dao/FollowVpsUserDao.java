package net.maku.followcom.dao;

import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.followcom.entity.FollowVpsUserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户vps可查看列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowVpsUserDao extends BaseDao<FollowVpsUserEntity> {

}