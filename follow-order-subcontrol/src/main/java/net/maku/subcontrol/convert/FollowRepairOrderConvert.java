package net.maku.subcontrol.convert;

import net.maku.subcontrol.entity.FollowRepairOrderEntity;
import net.maku.subcontrol.vo.FollowRepairOrderVO;
import net.maku.subcontrol.vo.FollowRepairOrderExcelVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 补单记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowRepairOrderConvert {
    FollowRepairOrderConvert INSTANCE = Mappers.getMapper(FollowRepairOrderConvert.class);

    FollowRepairOrderEntity convert(FollowRepairOrderVO vo);

    FollowRepairOrderVO convert(FollowRepairOrderEntity entity);

    List<FollowRepairOrderVO> convertList(List<FollowRepairOrderEntity> list);

    List<FollowRepairOrderEntity> convertList2(List<FollowRepairOrderVO> list);

    List<FollowRepairOrderExcelVO> convertExcelList(List<FollowRepairOrderEntity> list);

    List<FollowRepairOrderEntity> convertExcelList2(List<FollowRepairOrderExcelVO> list);
}