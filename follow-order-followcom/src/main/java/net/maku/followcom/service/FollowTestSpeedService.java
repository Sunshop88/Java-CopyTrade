package net.maku.followcom.service;

import net.maku.followcom.entity.FollowTestSpeedEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.query.FollowTestSpeedQuery;
import net.maku.followcom.vo.FollowTestDetailVO;
import net.maku.followcom.vo.FollowTestSpeedVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 测速记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowTestSpeedService extends BaseService<FollowTestSpeedEntity> {

    PageResult<FollowTestSpeedVO> page(FollowTestSpeedQuery query);

    FollowTestSpeedVO get(Long id);


    void save(FollowTestSpeedVO vo);

    void update(FollowTestSpeedVO vo);

    void delete(List<Long> idList);


    void export();

//    void measure(List<String> servers, List<String> vps);

    void saveTestSpeed(FollowTestSpeedVO overallResult);

    boolean measure(List<String> servers, FollowVpsEntity vpsEntity, Integer testId, LocalDateTime measureTime);

}