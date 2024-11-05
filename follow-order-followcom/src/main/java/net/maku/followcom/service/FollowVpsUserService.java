package net.maku.followcom.service;

import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.followcom.vo.FollowVpsUserVO;
import net.maku.followcom.query.FollowVpsUserQuery;
import net.maku.followcom.entity.FollowVpsUserEntity;
import java.util.List;

/**
 * 用户vps可查看列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowVpsUserService extends BaseService<FollowVpsUserEntity> {

    PageResult<FollowVpsUserVO> page(FollowVpsUserQuery query);

    FollowVpsUserVO get(Long id);


    void save(FollowVpsUserVO vo);

    void update(FollowVpsUserVO vo);

    void delete(List<Long> idList);


    void export();
}