package net.maku.followcom.service;

import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.query.FollowVpsQuery;
import net.maku.followcom.vo.FollowVpsVO;

import java.util.List;

/**
 * vps列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowVpsService extends BaseService<FollowVpsEntity> {

    PageResult<FollowVpsVO> page(FollowVpsQuery query);

    FollowVpsVO get(Long id);


    void save(FollowVpsVO vo);

    void update(FollowVpsVO vo);

    void delete(List<Integer> idList);


    void export();
}