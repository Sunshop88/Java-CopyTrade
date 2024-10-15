package net.maku.followcom.convert;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.vo.FollowPlatformExcelVO;
import net.maku.followcom.vo.FollowPlatformVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
* 平台管理
*
* @author 阿沐 babamu@126.com
* @since 1.0.0 2024-09-11
*/
@Mapper
public interface FollowPlatformConvert {

    FollowPlatformConvert INSTANCE = Mappers.getMapper(FollowPlatformConvert.class);

    FollowPlatformEntity convert(FollowPlatformVO vo);

    FollowPlatformVO convert(FollowPlatformEntity entity);

    List<FollowPlatformVO> convertList(List<FollowPlatformEntity> list);

    List<FollowPlatformEntity> convertList2(List<FollowPlatformVO> list);

    List<FollowPlatformExcelVO> convertExcelList(List<FollowPlatformEntity> list);

    List<FollowPlatformEntity> convertExcelList2(List<FollowPlatformExcelVO> list);
}