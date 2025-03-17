package net.maku.followcom.service;

import net.maku.followcom.entity.FollowVersionEntity;
import net.maku.followcom.query.FollowVersionQuery;
import net.maku.followcom.vo.FollowVersionVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import java.util.List;

/**
 * 项目版本
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowVersionService extends BaseService<FollowVersionEntity> {

    PageResult<FollowVersionVO> page(FollowVersionQuery query);

    FollowVersionVO get(Long id);


    void save(FollowVersionVO vo);

    void update(FollowVersionVO vo);

    void delete(List<Long> idList);

}