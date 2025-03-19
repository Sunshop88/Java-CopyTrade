package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowOrderSendEntity;
import net.maku.followcom.vo.FollowOrderSendVO;
import net.maku.followcom.vo.FollowOrderSendExcelVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.springframework.boot.web.server.MimeMappings.Mapping;

import java.util.List;

/**
 * 下单记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowOrderSendConvert {
    FollowOrderSendConvert INSTANCE = Mappers.getMapper(FollowOrderSendConvert.class);

    FollowOrderSendEntity convert(FollowOrderSendVO vo);

    FollowOrderSendVO convert(FollowOrderSendEntity entity);

    List<FollowOrderSendVO> convertList(List<FollowOrderSendEntity> list);

    List<FollowOrderSendEntity> convertList2(List<FollowOrderSendVO> list);

    List<FollowOrderSendExcelVO> convertExcelList(List<FollowOrderSendEntity> list);

    List<FollowOrderSendEntity> convertExcelList2(List<FollowOrderSendExcelVO> list);
}