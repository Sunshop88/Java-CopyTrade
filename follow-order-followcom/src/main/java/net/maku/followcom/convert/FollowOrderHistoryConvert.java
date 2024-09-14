package net.maku.followcom.convert;

import net.maku.business.entity.FollowOrderHistoryEntity;
import net.maku.business.vo.FollowOrderHistoryVO;
import net.maku.business.vo.FollowOrderHistoryExcelVO;
import org.mapstruct.Mapper;
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

    List<FollowOrderHistoryEntity> convertList2(List<FollowOrderHistoryVO> list);

    List<FollowOrderHistoryExcelVO> convertExcelList(List<FollowOrderHistoryEntity> list);

    List<FollowOrderHistoryEntity> convertExcelList2(List<FollowOrderHistoryExcelVO> list);
}