package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.vo.FollowTraderSubscribeVO;
import net.maku.followcom.vo.FollowTraderSubscribeExcelVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 订阅关系表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowTraderSubscribeConvert {
    FollowTraderSubscribeConvert INSTANCE = Mappers.getMapper(FollowTraderSubscribeConvert.class);

    FollowTraderSubscribeEntity convert(FollowTraderSubscribeVO vo);

    FollowTraderSubscribeVO convert(FollowTraderSubscribeEntity entity);

    List<FollowTraderSubscribeVO> convertList(List<FollowTraderSubscribeEntity> list);

    List<FollowTraderSubscribeEntity> convertList2(List<FollowTraderSubscribeVO> list);

    List<FollowTraderSubscribeExcelVO> convertExcelList(List<FollowTraderSubscribeEntity> list);

    List<FollowTraderSubscribeEntity> convertExcelList2(List<FollowTraderSubscribeExcelVO> list);
}