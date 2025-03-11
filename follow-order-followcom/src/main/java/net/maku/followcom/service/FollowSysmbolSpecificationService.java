package net.maku.followcom.service;

import net.maku.followcom.vo.FollowTraderVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.followcom.vo.FollowSysmbolSpecificationVO;
import net.maku.followcom.query.FollowSysmbolSpecificationQuery;
import net.maku.followcom.entity.FollowSysmbolSpecificationEntity;
import java.util.List;
import java.util.Map;

/**
 * 品种规格
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowSysmbolSpecificationService extends BaseService<FollowSysmbolSpecificationEntity> {

    PageResult<FollowSysmbolSpecificationVO> page(FollowSysmbolSpecificationQuery query);

    FollowSysmbolSpecificationVO get(Long id);


    void save(FollowSysmbolSpecificationVO vo);

    void update(FollowSysmbolSpecificationVO vo);

    void delete(List<Long> idList);


    void export();

    List<FollowSysmbolSpecificationEntity> getByTraderId(long traderId);

    PageResult<FollowSysmbolSpecificationVO> pageSpecification(FollowSysmbolSpecificationQuery query);
}