package net.maku.followcom.service;

import net.maku.followcom.entity.FollowOrderInstructEntity;
import net.maku.followcom.query.FollowOrderInstructQuery;
import net.maku.followcom.vo.FollowOrderInstructVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import java.util.List;

/**
 * 下单总指令表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowOrderInstructService extends BaseService<FollowOrderInstructEntity> {

    PageResult<FollowOrderInstructVO> page(FollowOrderInstructQuery query);

    FollowOrderInstructVO get(Long id);


    void save(FollowOrderInstructVO vo);

    void update(FollowOrderInstructVO vo);

    void delete(List<Long> idList);


    void export();
}