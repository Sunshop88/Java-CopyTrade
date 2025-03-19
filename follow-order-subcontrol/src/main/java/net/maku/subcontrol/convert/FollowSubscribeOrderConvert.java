package net.maku.subcontrol.convert;

import net.maku.followcom.vo.FollowSubscribeOrderExcelVO;
import net.maku.followcom.vo.FollowSubscribeOrderVO;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
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