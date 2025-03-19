package net.maku.followcom.convert;


import net.maku.followcom.entity.FollowTestDetailEntity;
import net.maku.followcom.vo.FollowTestDetailExcelVO;
import net.maku.followcom.vo.FollowTestDetailVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 测速详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowTestDetailConvert {
    FollowTestDetailConvert INSTANCE = Mappers.getMapper(FollowTestDetailConvert.class);

    FollowTestDetailEntity convert(FollowTestDetailVO vo);

    FollowTestDetailVO convert(FollowTestDetailEntity entity);

    List<FollowTestDetailVO> convertList(List<FollowTestDetailEntity> list);

    List<FollowTestDetailEntity> convertList2(List<FollowTestDetailVO> list);

    List<FollowTestDetailExcelVO> convertExcelList(List<FollowTestDetailEntity> list);

    List<FollowTestDetailEntity> convertExcelList2(List<FollowTestDetailExcelVO> list);
}