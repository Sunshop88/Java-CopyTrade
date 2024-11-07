package net.maku.subcontrol.service;

import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.subcontrol.vo.FollowRepairOrderVO;
import net.maku.subcontrol.query.FollowRepairOrderQuery;
import net.maku.subcontrol.entity.FollowRepairOrderEntity;
import java.util.List;

/**
 * 补单记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowRepairOrderService extends BaseService<FollowRepairOrderEntity> {

    PageResult<FollowRepairOrderVO> page(FollowRepairOrderQuery query);

    FollowRepairOrderVO get(Long id);


    void save(FollowRepairOrderVO vo);

    void update(FollowRepairOrderVO vo);

    void delete(List<Long> idList);


    void export();
}