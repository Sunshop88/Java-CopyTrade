package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowOrderDetailEntity;
import net.maku.followcom.vo.FollowOrderDetailVO;
import net.maku.followcom.vo.FollowOrderDetailExcelVO;
import net.maku.followcom.vo.OrderClosePageVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 订单详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowOrderDetailConvert {
    FollowOrderDetailConvert INSTANCE = Mappers.getMapper(FollowOrderDetailConvert.class);

    FollowOrderDetailEntity convert(FollowOrderDetailVO vo);

    FollowOrderDetailVO convert(FollowOrderDetailEntity entity);

    List<FollowOrderDetailVO> convertList(List<FollowOrderDetailEntity> list);

    List<FollowOrderDetailEntity> convertList2(List<FollowOrderDetailVO> list);

    List<FollowOrderDetailExcelVO> convertExcelList(List<FollowOrderDetailEntity> list);

    List<FollowOrderDetailEntity> convertExcelList2(List<FollowOrderDetailExcelVO> list);

    List<FollowOrderDetailExcelVO> convertExcelList3(List<FollowOrderDetailVO> list);
    @Mappings({//喊单账号
            @Mapping(source = "id", target = "id"),
            @Mapping(source = "orderNo", target = "ticket"),
            @Mapping(source = "openTime", target = "openTime"),
            @Mapping(source = "closeTime", target = "closeTime"),
            @Mapping(source = "type", target = "type"),
            @Mapping(source = "size", target = "lots"),
            @Mapping(source = "symbol", target = "symbol"),
            @Mapping(source = "openPrice", target = "openPrice"),
            @Mapping(source = "sl", target = "stopLoss"),
            @Mapping(source = "tp", target = "takeProfit"),
            @Mapping(source = "closePrice", target = "closePrice"),
            @Mapping(source = "swap", target = "swap"),
            @Mapping(source = "commission", target = "commission"),
            @Mapping(source = "magical", target = "magicNumber"),
            @Mapping(source = "profit", target = "profit"),
            @Mapping(source = "placedType", target = "placeType")
    })
    List<OrderClosePageVO.OrderVo> convertOrderList(List<FollowOrderDetailEntity> list);




}