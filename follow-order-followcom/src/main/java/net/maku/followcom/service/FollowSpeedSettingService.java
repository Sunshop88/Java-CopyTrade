package net.maku.followcom.service;

import net.maku.followcom.entity.FollowSpeedSettingEntity;
import net.maku.followcom.query.FollowSpeedSettingQuery;
import net.maku.followcom.vo.FollowSpeedSettingVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;

import java.util.List;

/**
 * 测速配置
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowSpeedSettingService extends BaseService<FollowSpeedSettingEntity> {

    PageResult<FollowSpeedSettingVO> page(FollowSpeedSettingQuery query);

    FollowSpeedSettingVO get(Long id);

    void save(FollowSpeedSettingVO vo);

    void update(FollowSpeedSettingVO vo);

    void delete(List<Long> idList);

}