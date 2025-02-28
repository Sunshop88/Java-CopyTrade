package net.maku.followcom.service;

import net.maku.followcom.entity.FollowOrderInstructSubEntity;
import net.maku.followcom.query.FollowOrderInstructSubQuery;
import net.maku.followcom.vo.FollowOrderInstructSubVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import java.util.List;

/**
 * 下单子指令
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowOrderInstructSubService extends BaseService<FollowOrderInstructSubEntity> {

    PageResult<FollowOrderInstructSubVO> page(FollowOrderInstructSubQuery query);

    FollowOrderInstructSubVO get(Long id);


    void save(FollowOrderInstructSubVO vo);

    void update(FollowOrderInstructSubVO vo);

    void delete(List<Long> idList);


    void export();
}