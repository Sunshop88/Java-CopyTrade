package net.maku.followcom.service;

import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.followcom.vo.FollowOrderSendVO;
import net.maku.followcom.query.FollowOrderSendQuery;
import net.maku.followcom.entity.FollowOrderSendEntity;
import java.util.List;

/**
 * 下单记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowOrderSendService extends BaseService<FollowOrderSendEntity> {

    PageResult<FollowOrderSendVO> page(FollowOrderSendQuery query);

    FollowOrderSendVO get(Long id);


    void save(FollowOrderSendVO vo);

    void update(FollowOrderSendVO vo);

    void delete(List<Long> idList);


    void export();
}