package net.maku.followcom.dao;

import net.maku.followcom.entity.FollowTraderUserEntity;
import net.maku.followcom.vo.FollowTraderCountVO;
import net.maku.framework.mybatis.dao.BaseDao;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 账号初始表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowTraderUserDao extends BaseDao<FollowTraderUserEntity> {

    @Select("SELECT platform AS serverName, COUNT(1) AS nodeCount FROM follow_trader_user GROUP BY platform")
    List<FollowTraderCountVO> getServerNodeCounts();
}