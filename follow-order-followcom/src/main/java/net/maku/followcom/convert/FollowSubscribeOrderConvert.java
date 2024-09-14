package net.maku.followcom.convert;

import net.maku.business.entity.FollowSubscribeOrderEntity;
import net.maku.business.vo.FollowSubscribeOrderVO;
import net.maku.business.vo.FollowSubscribeOrderExcelVO;
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
public interface FollowSubscribeOrderConvert {
    FollowSubscribeOrderConvert INSTANCE = Mappers.getMapper(FollowSubscribeOrderConvert.class);

    FollowSubscribeOrderEntity convert(FollowSubscribeOrderVO vo);

    FollowSubscribeOrderVO convert(FollowSubscribeOrderEntity entity);

    List<FollowSubscribeOrderVO> convertList(List<FollowSubscribeOrderEntity> list);

    List<FollowSubscribeOrderEntity> convertList2(List<FollowSubscribeOrderVO> list);

    List<FollowSubscribeOrderExcelVO> convertExcelList(List<FollowSubscribeOrderEntity> list);

    List<FollowSubscribeOrderEntity> convertExcelList2(List<FollowSubscribeOrderExcelVO> list);
}