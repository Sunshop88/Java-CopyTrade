package net.maku.followcom.dao;

import net.maku.followcom.entity.FollowTestDetailEntity;
import net.maku.followcom.query.FollowTestServerQuery;
import net.maku.followcom.vo.FollowTestDetailVO;
import net.maku.framework.mybatis.dao.BaseDao;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 测速详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowTestDetailDao extends BaseDao<FollowTestDetailEntity> {

    List<FollowTestDetailVO> selectServer(@Param("query")FollowTestServerQuery query);

    List<FollowTestDetailVO> selectServerNode(@Param("query")FollowTestServerQuery query);
}