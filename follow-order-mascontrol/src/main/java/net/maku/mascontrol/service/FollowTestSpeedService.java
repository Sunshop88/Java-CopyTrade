package net.maku.mascontrol.service;

import net.maku.followcom.vo.FollowBrokeServerVO;
import net.maku.followcom.vo.FollowVpsVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.mascontrol.entity.FollowTestDetailEntity;
import net.maku.mascontrol.entity.FollowTestSpeedEntity;
import net.maku.mascontrol.query.FollowTestSpeedQuery;
import net.maku.mascontrol.vo.FollowTestDetailVO;
import net.maku.mascontrol.vo.FollowTestSpeedVO;

import java.util.Date;
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

    void measure(List<String> servers, List<String> vps);

    void remeasure(Long id,List<String> servers, List<String> vps);
}