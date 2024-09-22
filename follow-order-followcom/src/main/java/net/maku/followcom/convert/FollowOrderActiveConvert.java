package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowOrderActiveEntity;
import net.maku.followcom.vo.FollowOrderActiveVO;
import net.maku.followcom.vo.FollowOrderActiveExcelVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 账号持仓订单
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowOrderActiveConvert {
    FollowOrderActiveConvert INSTANCE = Mappers.getMapper(FollowOrderActiveConvert.class);

    FollowOrderActiveEntity convert(FollowOrderActiveVO vo);

    FollowOrderActiveVO convert(FollowOrderActiveEntity entity);

    List<FollowOrderActiveVO> convertList(List<FollowOrderActiveEntity> list);

    List<FollowOrderActiveEntity> convertList2(List<FollowOrderActiveVO> list);

    List<FollowOrderActiveExcelVO> convertExcelList(List<FollowOrderActiveEntity> list);

    List<FollowOrderActiveEntity> convertExcelList2(List<FollowOrderActiveExcelVO> list);
}