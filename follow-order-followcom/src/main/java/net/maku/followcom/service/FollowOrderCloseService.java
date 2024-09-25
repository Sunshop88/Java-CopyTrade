package net.maku.followcom.service;

import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.followcom.vo.FollowOrderCloseVO;
import net.maku.followcom.query.FollowOrderCloseQuery;
import net.maku.followcom.entity.FollowOrderCloseEntity;
import java.util.List;

/**
 * 平仓记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowOrderCloseService extends BaseService<FollowOrderCloseEntity> {

    PageResult<FollowOrderCloseVO> page(FollowOrderCloseQuery query);

    FollowOrderCloseVO get(Long id);


    void save(FollowOrderCloseVO vo);

    void update(FollowOrderCloseVO vo);

    void delete(List<Long> idList);


    void export();
}