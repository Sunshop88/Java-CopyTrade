package net.maku.followcom.service;

import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.business.vo.FollowSubscribeOrderVO;
import net.maku.business.query.FollowSubscribeOrderQuery;
import net.maku.business.entity.FollowSubscribeOrderEntity;
import java.util.List;

/**
 * 订阅关系表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowSubscribeOrderService extends BaseService<FollowSubscribeOrderEntity> {

    PageResult<FollowSubscribeOrderVO> page(FollowSubscribeOrderQuery query);

    FollowSubscribeOrderVO get(Long id);


    void save(FollowSubscribeOrderVO vo);

    void update(FollowSubscribeOrderVO vo);

    void delete(List<Long> idList);


    void export();
}