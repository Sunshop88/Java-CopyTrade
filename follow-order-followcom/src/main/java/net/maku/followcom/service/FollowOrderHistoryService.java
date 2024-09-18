package net.maku.followcom.service;

import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.followcom.vo.FollowOrderHistoryVO;
import net.maku.followcom.query.FollowOrderHistoryQuery;
import net.maku.followcom.entity.FollowOrderHistoryEntity;
import java.util.List;

/**
 * 所有MT4账号的历史订单
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowOrderHistoryService extends BaseService<FollowOrderHistoryEntity> {

    PageResult<FollowOrderHistoryVO> page(FollowOrderHistoryQuery query);

    FollowOrderHistoryVO get(Long id);


    void save(FollowOrderHistoryVO vo);

    void update(FollowOrderHistoryVO vo);

    void delete(List<Long> idList);


    void export();
}