package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowVarietyEntity;
import net.maku.followcom.vo.FollowVarietyExcelVO;
import net.maku.followcom.vo.FollowVarietyVO;
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