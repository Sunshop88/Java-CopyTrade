package net.maku.followcom.dao;

import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.business.entity.FollowTraderLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 交易日志
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowTraderLogDao extends BaseDao<FollowTraderLogEntity> {

}