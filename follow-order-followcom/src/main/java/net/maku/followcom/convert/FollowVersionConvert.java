package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowVersionEntity;
import net.maku.followcom.vo.FollowVersionVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 项目版本
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowVersionConvert {
    FollowVersionConvert INSTANCE = Mappers.getMapper(FollowVersionConvert.class);

    FollowVersionEntity convert(FollowVersionVO vo);

    FollowVersionVO convert(FollowVersionEntity entity);

    List<FollowVersionVO> convertList(List<FollowVersionEntity> list);

    List<FollowVersionEntity> convertList2(List<FollowVersionVO> list);

}