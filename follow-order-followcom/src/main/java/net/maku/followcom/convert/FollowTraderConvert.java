package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.vo.FollowTraderVO;
import net.maku.followcom.vo.FollowTraderExcelVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * mt4账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowTraderConvert {
    FollowTraderConvert INSTANCE = Mappers.getMapper(FollowTraderConvert.class);

    FollowTraderEntity convert(FollowTraderVO vo);

    FollowTraderVO convert(FollowTraderEntity entity);

    List<FollowTraderVO> convertList(List<FollowTraderEntity> list);

    List<FollowTraderEntity> convertList2(List<FollowTraderVO> list);

    List<FollowTraderExcelVO> convertExcelList(List<FollowTraderEntity> list);

    List<FollowTraderEntity> convertExcelList2(List<FollowTraderExcelVO> list);
}