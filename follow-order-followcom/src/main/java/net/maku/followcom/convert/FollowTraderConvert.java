package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.FollowModeEnum;
import net.maku.followcom.vo.*;
import org.mapstruct.*;
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

    // @BeforeMapping 注解可以让我们在实际映射之前执行自定义处理
    @BeforeMapping
    default void beforeMapping(SourceInsertVO sourceInsertVO, @MappingTarget FollowTraderEntity followTraderEntity) {
        // 如果需要处理 Boolean 转 Integer
        if (sourceInsertVO.getStatus() != null) {
            followTraderEntity.setFollowStatus(sourceInsertVO.getStatus() ? 1 : 0); // true 转 1，false 转 0
        }
    }
    @Mappings({//喊单账号
            @Mapping(source = "status", target = "status", qualifiedByName = "booleanToInt"),
    })
    FollowTraderVO convert(SourceInsertVO sourceInsertVO);

/*    @Mappings({//喊单账号
            @Mapping(source = "status", target = "status", qualifiedByName = "booleanToInt")
    })
    FollowTraderVO convert(SourceUpdateVO sourceUpdateVO);*/

    @BeforeMapping
    default void beforeMapping(SourceUpdateVO sourceUpdateVO, @MappingTarget FollowTraderEntity followTraderEntity) {
        // 如果需要处理 Boolean 转 Integer
        if (sourceUpdateVO.getStatus() != null) {
            followTraderEntity.setFollowStatus(sourceUpdateVO.getStatus() ? 1 : 0); // true 转 1，false 转 0
        }
    }
    @Mappings({//喊单账号
            @Mapping(source = "status", target = "status", qualifiedByName = "booleanToInt"),
    })
    FollowTraderEntity convert(SourceUpdateVO sourceUpdateVO);

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

    @Mappings({//喊单账号
            @Mapping(source = "type", target = "type", qualifiedByName = "intToString"),
            @Mapping(source = "remark", target = "comment"),
            @Mapping(source = "platform", target = "platformName"),
            @Mapping(source = "followStatus", target = "status", qualifiedByName = "intToBoolean"),
            @Mapping(source = "account", target = "user"),
            @Mapping(source = "euqit", target = "equity"),
            @Mapping(source = "marginProportion", target = "margin"),
            @Mapping(source = "serverName", target = "server")
    })
    AccountCacheVO convertCache(FollowTraderEntity entity);

    @Named("apiCodeToCode")
    default Integer apiCodeToCode(Integer apiCode) {

        return FollowModeEnum.getVal(apiCode);
    }

    @Named("intToBoolean")
    default Boolean intToBoolean(Integer status) {
        if (status == null) {
            return null;
        }
        return status == 0 ? false : true;
    }

    @Named("booleanToInt")
    default Integer booleanToInt(Boolean status) {
        if (status == null) {
            return null;
        }
        return status ? 1 : 0;
    }

    @Named("intToString")
    default String intToString(Integer type) {
        if (type == null) {
            return null;
        }
        return type == 0 ? "SOURCE" : "FOLLOW";
    }

    List<DashboardAccountDataVO> convertAccountData(List<FollowTraderEntity> records);
}