package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowSpeedSettingEntity;
import net.maku.followcom.vo.FollowSpeedSettingVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 测速配置
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowSpeedSettingConvert {
    FollowSpeedSettingConvert INSTANCE = Mappers.getMapper(FollowSpeedSettingConvert.class);

    FollowSpeedSettingEntity convert(FollowSpeedSettingVO vo);

    FollowSpeedSettingVO convert(FollowSpeedSettingEntity entity);

    List<FollowSpeedSettingVO> convertList(List<FollowSpeedSettingEntity> list);

    List<FollowSpeedSettingEntity> convertList2(List<FollowSpeedSettingVO> list);
}