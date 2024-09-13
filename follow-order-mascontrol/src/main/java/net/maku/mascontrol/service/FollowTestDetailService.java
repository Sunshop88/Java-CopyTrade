package net.maku.mascontrol.service;

import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.mascontrol.vo.FollowTestDetailVO;
import net.maku.mascontrol.query.FollowTestDetailQuery;
import net.maku.mascontrol.entity.FollowTestDetailEntity;
import java.util.List;

/**
 * 测速详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowTestDetailService extends BaseService<FollowTestDetailEntity> {

    PageResult<FollowTestDetailVO> page(FollowTestDetailQuery query);

    FollowTestDetailVO get(Long id);


    void save(FollowTestDetailVO vo);

    void update(FollowTestDetailVO vo);

    void delete(List<Long> idList);


    void export();
}