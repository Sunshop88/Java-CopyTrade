package net.maku.subcontrol.service;

import net.maku.subcontrol.query.FollowOrderHistoryQuery;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.subcontrol.entity.FollowOrderHistoryEntity;
import net.maku.subcontrol.vo.FollowOrderHistoryVO;

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