package net.maku.followcom.dao;

import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.business.entity.FollowTraderEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * mt4账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowTraderDao extends BaseDao<FollowTraderEntity> {

}