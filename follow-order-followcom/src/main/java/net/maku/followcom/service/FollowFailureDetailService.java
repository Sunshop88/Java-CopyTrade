package net.maku.followcom.service;

import net.maku.followcom.entity.FollowFailureDetailEntity;
import net.maku.followcom.query.FollowFailureDetailQuery;
import net.maku.followcom.vo.FollowFailureDetailVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import java.util.List;

/**
 * 失败详情表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowFailureDetailService extends BaseService<FollowFailureDetailEntity> {

    PageResult<FollowFailureDetailVO> page(FollowFailureDetailQuery query);

    FollowFailureDetailVO get(Long id);


    void save(FollowFailureDetailVO vo);

    void update(FollowFailureDetailVO vo);

    void delete(List<Long> idList);


}