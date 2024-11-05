package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowVpsUserEntity;
import net.maku.followcom.vo.FollowVpsUserVO;
import net.maku.followcom.vo.FollowVpsUserExcelVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 用户vps可查看列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowVpsUserConvert {
    FollowVpsUserConvert INSTANCE = Mappers.getMapper(FollowVpsUserConvert.class);

    FollowVpsUserEntity convert(FollowVpsUserVO vo);

    FollowVpsUserVO convert(FollowVpsUserEntity entity);

    List<FollowVpsUserVO> convertList(List<FollowVpsUserEntity> list);

    List<FollowVpsUserEntity> convertList2(List<FollowVpsUserVO> list);

    List<FollowVpsUserExcelVO> convertExcelList(List<FollowVpsUserEntity> list);

    List<FollowVpsUserEntity> convertExcelList2(List<FollowVpsUserExcelVO> list);
}