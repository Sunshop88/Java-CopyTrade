package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowOrderCloseEntity;
import net.maku.followcom.vo.FollowOrderCloseVO;
import net.maku.followcom.vo.FollowOrderCloseExcelVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 平仓记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowOrderCloseConvert {
    FollowOrderCloseConvert INSTANCE = Mappers.getMapper(FollowOrderCloseConvert.class);

    FollowOrderCloseEntity convert(FollowOrderCloseVO vo);

    FollowOrderCloseVO convert(FollowOrderCloseEntity entity);

    List<FollowOrderCloseVO> convertList(List<FollowOrderCloseEntity> list);

    List<FollowOrderCloseEntity> convertList2(List<FollowOrderCloseVO> list);

    List<FollowOrderCloseExcelVO> convertExcelList(List<FollowOrderCloseEntity> list);

    List<FollowOrderCloseEntity> convertExcelList2(List<FollowOrderCloseExcelVO> list);
}