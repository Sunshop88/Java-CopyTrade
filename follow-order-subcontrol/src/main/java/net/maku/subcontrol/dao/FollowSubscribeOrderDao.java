package net.maku.subcontrol.dao;

import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订阅关系表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowSubscribeOrderDao extends BaseDao<FollowSubscribeOrderEntity> {

}