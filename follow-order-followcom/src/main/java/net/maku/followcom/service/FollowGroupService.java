package net.maku.followcom.service;

import net.maku.followcom.entity.FollowGroupEntity;
import net.maku.followcom.query.FollowGroupQuery;
import net.maku.followcom.vo.FollowGroupVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import java.util.List;

/**
 * 组别
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowGroupService extends BaseService<FollowGroupEntity> {

    PageResult<FollowGroupVO> page(FollowGroupQuery query);

    FollowGroupVO get(Long id);


    void save(FollowGroupVO vo);

    void update(FollowGroupVO vo);

    void delete(List<Long> idList);


    void export();
}