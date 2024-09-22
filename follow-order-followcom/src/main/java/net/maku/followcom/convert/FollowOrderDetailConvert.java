package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowOrderDetailEntity;
import net.maku.followcom.vo.FollowOrderDetailVO;
import net.maku.followcom.vo.FollowOrderDetailExcelVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 订单详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowOrderDetailConvert {
    FollowOrderDetailConvert INSTANCE = Mappers.getMapper(FollowOrderDetailConvert.class);

    FollowOrderDetailEntity convert(FollowOrderDetailVO vo);

    FollowOrderDetailVO convert(FollowOrderDetailEntity entity);

    List<FollowOrderDetailVO> convertList(List<FollowOrderDetailEntity> list);

    List<FollowOrderDetailEntity> convertList2(List<FollowOrderDetailVO> list);

    List<FollowOrderDetailExcelVO> convertExcelList(List<FollowOrderDetailEntity> list);

    List<FollowOrderDetailEntity> convertExcelList2(List<FollowOrderDetailExcelVO> list);
}