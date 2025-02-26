package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowFailureDetailEntity;
import net.maku.followcom.vo.FollowFailureDetailVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 失败详情表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowFailureDetailConvert {
    FollowFailureDetailConvert INSTANCE = Mappers.getMapper(FollowFailureDetailConvert.class);

    FollowFailureDetailEntity convert(FollowFailureDetailVO vo);

    FollowFailureDetailVO convert(FollowFailureDetailEntity entity);

    List<FollowFailureDetailVO> convertList(List<FollowFailureDetailEntity> list);

    List<FollowFailureDetailEntity> convertList2(List<FollowFailureDetailVO> list);

}