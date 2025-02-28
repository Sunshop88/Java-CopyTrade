package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowOrderInstructSubEntity;
import net.maku.followcom.vo.FollowOrderInstructSubExcelVO;
import net.maku.followcom.vo.FollowOrderInstructSubVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 下单子指令
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowOrderInstructSubConvert {
    FollowOrderInstructSubConvert INSTANCE = Mappers.getMapper(FollowOrderInstructSubConvert.class);

    FollowOrderInstructSubEntity convert(FollowOrderInstructSubVO vo);

    FollowOrderInstructSubVO convert(FollowOrderInstructSubEntity entity);

    List<FollowOrderInstructSubVO> convertList(List<FollowOrderInstructSubEntity> list);

    List<FollowOrderInstructSubEntity> convertList2(List<FollowOrderInstructSubVO> list);

    List<FollowOrderInstructSubExcelVO> convertExcelList(List<FollowOrderInstructSubEntity> list);

    List<FollowOrderInstructSubEntity> convertExcelList2(List<FollowOrderInstructSubExcelVO> list);
}