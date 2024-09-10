package net.maku.mascontrol.convert;

import net.maku.mascontrol.entity.FollowVpsEntity;
import net.maku.mascontrol.vo.FollowVpsExcelVO;
import net.maku.mascontrol.vo.FollowVpsVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * vps列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowVpsConvert {
    FollowVpsConvert INSTANCE = Mappers.getMapper(FollowVpsConvert.class);

    FollowVpsEntity convert(FollowVpsVO vo);

    FollowVpsVO convert(FollowVpsEntity entity);

    List<FollowVpsVO> convertList(List<FollowVpsEntity> list);

    List<FollowVpsEntity> convertList2(List<FollowVpsVO> list);

    List<FollowVpsExcelVO> convertExcelList(List<FollowVpsEntity> list);

    List<FollowVpsEntity> convertExcelList2(List<FollowVpsExcelVO> list);
}