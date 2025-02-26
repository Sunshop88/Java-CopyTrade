package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowGroupEntity;
import net.maku.followcom.vo.FollowGroupExcelVO;
import net.maku.followcom.vo.FollowGroupVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 组别
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowGroupConvert {
    FollowGroupConvert INSTANCE = Mappers.getMapper(FollowGroupConvert.class);

    FollowGroupEntity convert(FollowGroupVO vo);

    FollowGroupVO convert(FollowGroupEntity entity);

    List<FollowGroupVO> convertList(List<FollowGroupEntity> list);

    List<FollowGroupEntity> convertList2(List<FollowGroupVO> list);

    List<FollowGroupExcelVO> convertExcelList(List<FollowGroupEntity> list);

    List<FollowGroupEntity> convertExcelList2(List<FollowGroupExcelVO> list);
}