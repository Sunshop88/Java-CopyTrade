package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowSysmbolSpecificationEntity;
import net.maku.followcom.vo.FollowSysmbolSpecificationVO;
import net.maku.followcom.vo.FollowSysmbolSpecificationExcelVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 品种规格
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowSysmbolSpecificationConvert {
    FollowSysmbolSpecificationConvert INSTANCE = Mappers.getMapper(FollowSysmbolSpecificationConvert.class);

    FollowSysmbolSpecificationEntity convert(FollowSysmbolSpecificationVO vo);

    FollowSysmbolSpecificationVO convert(FollowSysmbolSpecificationEntity entity);

    List<FollowSysmbolSpecificationVO> convertList(List<FollowSysmbolSpecificationEntity> list);

    List<FollowSysmbolSpecificationEntity> convertList2(List<FollowSysmbolSpecificationVO> list);

    List<FollowSysmbolSpecificationExcelVO> convertExcelList(List<FollowSysmbolSpecificationEntity> list);

    List<FollowSysmbolSpecificationEntity> convertExcelList2(List<FollowSysmbolSpecificationExcelVO> list);
}