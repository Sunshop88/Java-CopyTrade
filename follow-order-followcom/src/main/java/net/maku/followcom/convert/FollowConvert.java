package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowEntity;
import net.maku.followcom.vo.FollowInsertVO;
import net.maku.followcom.vo.FollowUpdateVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * Author:  zsd
 * Date:  2024/11/15/周五 15:05
 * 跟单者
 */

@Mapper
public interface FollowConvert {
    FollowConvert INSTANCE = Mappers.getMapper(FollowConvert.class);

    FollowEntity convert(FollowInsertVO vo);

    FollowEntity convert(FollowUpdateVO vo);

}