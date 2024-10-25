package net.maku.mascontrol.convert;

import net.maku.mascontrol.entity.FollowTestSpeedEntity;
import net.maku.mascontrol.vo.FollowTestSpeedVO;
import net.maku.mascontrol.vo.FollowTestSpeedExcelVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 测速记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowTestSpeedConvert {
    FollowTestSpeedConvert INSTANCE = Mappers.getMapper(FollowTestSpeedConvert.class);

    FollowTestSpeedEntity convert(FollowTestSpeedVO vo);

    FollowTestSpeedVO convert(FollowTestSpeedEntity entity);

    List<FollowTestSpeedVO> convertList(List<FollowTestSpeedEntity> list);

    List<FollowTestSpeedEntity> convertList2(List<FollowTestSpeedVO> list);

    List<FollowTestSpeedExcelVO> convertExcelList(List<FollowTestSpeedEntity> list);

    List<FollowTestSpeedEntity> convertExcelList2(List<FollowTestSpeedExcelVO> list);
}