package net.maku.followcom.dao;

import net.maku.followcom.query.FollowSysmbolSpecificationQuery;
import net.maku.followcom.vo.FollowSysmbolSpecificationVO;
import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.followcom.entity.FollowSysmbolSpecificationEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 品种规格
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowSysmbolSpecificationDao extends BaseDao<FollowSysmbolSpecificationEntity> {

    List<FollowSysmbolSpecificationVO> selectSpecification(FollowSysmbolSpecificationQuery query);
}