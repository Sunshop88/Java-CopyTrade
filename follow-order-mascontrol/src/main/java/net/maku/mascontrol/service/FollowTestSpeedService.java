package net.maku.mascontrol.service;

import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.mascontrol.entity.FollowTestSpeedEntity;
import net.maku.mascontrol.query.FollowTestSpeedQuery;
import net.maku.mascontrol.vo.FollowTestSpeedVO;

import java.util.List;

/**
 * 测速记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowTestSpeedService extends BaseService<FollowTestSpeedEntity> {

    PageResult<FollowTestSpeedVO> page(FollowTestSpeedQuery query);

    FollowTestSpeedVO get(Long id);


    void save(FollowTestSpeedVO vo);

    void update(FollowTestSpeedVO vo);

    void delete(List<Long> idList);


    void export();
}