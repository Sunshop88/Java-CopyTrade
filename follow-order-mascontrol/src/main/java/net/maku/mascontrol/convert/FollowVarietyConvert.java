package net.maku.mascontrol.convert;

import net.maku.mascontrol.entity.FollowVarietyEntity;
import net.maku.mascontrol.vo.FollowVarietyVO;
import net.maku.mascontrol.vo.FollowVarietyExcelVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 品种匹配
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowVarietyConvert {
    FollowVarietyConvert INSTANCE = Mappers.getMapper(FollowVarietyConvert.class);

    FollowVarietyEntity convert(FollowVarietyVO vo);

    FollowVarietyVO convert(FollowVarietyEntity entity);

    List<FollowVarietyVO> convertList(List<FollowVarietyEntity> list);

    List<FollowVarietyEntity> convertList2(List<FollowVarietyVO> list);

    List<FollowVarietyExcelVO> convertExcelList(List<FollowVarietyEntity> list);

    List<FollowVarietyEntity> convertExcelList2(List<FollowVarietyExcelVO> list);
}