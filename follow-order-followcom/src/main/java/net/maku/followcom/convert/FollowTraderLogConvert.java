package net.maku.followcom.convert;

import net.maku.business.entity.FollowTraderLogEntity;
import net.maku.business.vo.FollowTraderLogVO;
import net.maku.business.vo.FollowTraderLogExcelVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 交易日志
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowTraderLogConvert {
    FollowTraderLogConvert INSTANCE = Mappers.getMapper(FollowTraderLogConvert.class);

    FollowTraderLogEntity convert(FollowTraderLogVO vo);

    FollowTraderLogVO convert(FollowTraderLogEntity entity);

    List<FollowTraderLogVO> convertList(List<FollowTraderLogEntity> list);

    List<FollowTraderLogEntity> convertList2(List<FollowTraderLogVO> list);

    List<FollowTraderLogExcelVO> convertExcelList(List<FollowTraderLogEntity> list);

    List<FollowTraderLogEntity> convertExcelList2(List<FollowTraderLogExcelVO> list);
}