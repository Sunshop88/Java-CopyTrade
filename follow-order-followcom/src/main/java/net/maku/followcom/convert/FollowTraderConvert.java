package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.FollowModeEnum;
import net.maku.followcom.vo.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * mt4账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowTraderConvert {
    FollowTraderConvert INSTANCE = Mappers.getMapper(FollowTraderConvert.class);

    FollowTraderEntity convert(FollowTraderVO vo);

    FollowTraderVO convert(FollowTraderEntity entity);

    List<FollowTraderVO> convertList(List<FollowTraderEntity> list);

    List<FollowTraderEntity> convertList2(List<FollowTraderVO> list);

    List<FollowTraderExcelVO> convertExcelList(List<FollowTraderEntity> list);

    List<FollowTraderEntity> convertExcelList2(List<FollowTraderExcelVO> list);

    FollowTraderVO convert(SourceInsertVO sourceInsertVO);

    FollowTraderVO convert(SourceUpdateVO sourceUpdateVO);

    @Mappings({//喊单账号
            @Mapping(source = "sourceId", target = "traderId"),
            @Mapping(source = "user", target = "account"),
            @Mapping(source = "comment", target = "remark"),
            @Mapping(source = "direction", target = "followDirection"),
            @Mapping(source = "mode", target = "followMode", qualifiedByName = "apiCodeToCode"),
            @Mapping(source = "modeValue", target = "followParam"),
            @Mapping(source = "status", target = "followStatus"),
            @Mapping(source = "openOrderStatus", target = "followOpen"),
            @Mapping(source = "closeOrderStatus", target = "followClose"),
            @Mapping(source = "repairStatus", target = "followRep"),
    })
    FollowAddSalveVo convert(FollowInsertVO followInsertVO);

    @Mappings({//喊单账号
            @Mapping(source = "comment", target = "remark"),
            @Mapping(source = "direction", target = "followDirection"),
            @Mapping(source = "mode", target = "followMode", qualifiedByName = "apiCodeToCode"),
            @Mapping(source = "modeValue", target = "followParam"),
            @Mapping(source = "status", target = "followStatus"),
            @Mapping(source = "openOrderStatus", target = "followOpen"),
            @Mapping(source = "closeOrderStatus", target = "followClose"),
            @Mapping(source = "repairStatus", target = "followRep"),
    })
    FollowUpdateSalveVo convert(FollowUpdateVO followUpdateVO);

    @Named("apiCodeToCode")
    default Integer apiCodeToCode(Integer apiCode) {

        return FollowModeEnum.getVal(apiCode);
    }

}