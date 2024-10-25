package net.maku.followcom.service;

import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.followcom.vo.FollowTraderLogVO;
import net.maku.followcom.query.FollowTraderLogQuery;
import net.maku.followcom.entity.FollowTraderLogEntity;
import java.util.List;

/**
 * 交易日志
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowTraderLogService extends BaseService<FollowTraderLogEntity> {

    PageResult<FollowTraderLogVO> page(FollowTraderLogQuery query);

    FollowTraderLogVO get(Long id);


    void save(FollowTraderLogVO vo);

    void update(FollowTraderLogVO vo);

    void delete(List<Long> idList);


    void export();
}