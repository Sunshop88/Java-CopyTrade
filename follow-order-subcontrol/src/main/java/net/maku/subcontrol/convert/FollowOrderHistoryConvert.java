package net.maku.subcontrol.convert;

import net.maku.followcom.entity.FollowOrderDetailEntity;
import net.maku.followcom.vo.FollowOrderDetailVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.subcontrol.entity.FollowOrderHistoryEntity;
import net.maku.subcontrol.vo.FollowOrderHistoryExcelVO;
import net.maku.subcontrol.vo.FollowOrderHistoryVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 所有MT4账号的历史订单
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowOrderHistoryConvert {
    FollowOrderHistoryConvert INSTANCE = Mappers.getMapper(FollowOrderHistoryConvert.class);

    FollowOrderHistoryEntity convert(FollowOrderHistoryVO vo);

    FollowOrderHistoryVO convert(FollowOrderHistoryEntity entity);

    List<FollowOrderHistoryVO> convertList(List<FollowOrderHistoryEntity> list);
    @Mappings({//喊单账号
            @Mapping(source = "magical", target = "magic")
    })
     FollowOrderHistoryVO convert(FollowOrderDetailVO vo);
    List<FollowOrderHistoryVO> convertDetailList(List<FollowOrderDetailVO> list);

    List<FollowOrderHistoryEntity> convertList2(List<FollowOrderHistoryVO> list);

    List<FollowOrderHistoryExcelVO> convertExcelList(List<FollowOrderHistoryEntity> list);

    List<FollowOrderHistoryEntity> convertExcelList2(List<FollowOrderHistoryExcelVO> list);
}