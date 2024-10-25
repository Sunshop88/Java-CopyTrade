package net.maku.followcom.service;

import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.followcom.vo.FollowOrderActiveVO;
import net.maku.followcom.query.FollowOrderActiveQuery;
import net.maku.followcom.entity.FollowOrderActiveEntity;
import java.util.List;

/**
 * 账号持仓订单
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowOrderActiveService extends BaseService<FollowOrderActiveEntity> {

    PageResult<FollowOrderActiveVO> page(FollowOrderActiveQuery query);

    FollowOrderActiveVO get(Long id);


    void save(FollowOrderActiveVO vo);

    void update(FollowOrderActiveVO vo);

    void delete(List<Long> idList);


    void export();

}