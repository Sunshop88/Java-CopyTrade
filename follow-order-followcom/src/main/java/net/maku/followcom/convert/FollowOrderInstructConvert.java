package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowOrderInstructEntity;
import net.maku.followcom.vo.FollowOrderInstructVO;
import net.maku.mascontrol.vo.FollowOrderInstructExcelVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 下单总指令表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowOrderInstructConvert {
    FollowOrderInstructConvert INSTANCE = Mappers.getMapper(FollowOrderInstructConvert.class);

    FollowOrderInstructEntity convert(FollowOrderInstructVO vo);

    FollowOrderInstructVO convert(FollowOrderInstructEntity entity);

    List<FollowOrderInstructVO> convertList(List<FollowOrderInstructEntity> list);

    List<FollowOrderInstructEntity> convertList2(List<FollowOrderInstructVO> list);

    List<FollowOrderInstructExcelVO> convertExcelList(List<FollowOrderInstructEntity> list);

    List<FollowOrderInstructEntity> convertExcelList2(List<FollowOrderInstructExcelVO> list);
}