package net.maku.followcom.convert;

/**
 * Author:  zsd
 * Date:  2024/11/15/周五 15:05
 */

import net.maku.followcom.entity.SourceEntity;
import net.maku.followcom.vo.SourceInsertVO;
import net.maku.followcom.vo.SourceUpdateVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

@Mapper
public interface SourceConvert {
    SourceConvert INSTANCE = Mappers.getMapper(SourceConvert.class);

    @Mappings({//喊单账号
            @Mapping(source = "serverId", target = "clientId"),
            @Mapping(source = "account", target = "user"),
            @Mapping(source = "status", target = "status", qualifiedByName = "booleanToInt"),
            @Mapping(source = "remark", target = "comment")
    })
    SourceEntity convert(SourceInsertVO sourceInsertVO);

    @Mappings({//喊单账号
            @Mapping(source = "serverId", target = "clientId"),
            @Mapping(source = "status", target = "status", qualifiedByName = "booleanToInt"),
            @Mapping(source = "remark", target = "comment")
    })
    SourceEntity convert(SourceUpdateVO sourceUpdateVO);

    @Named("booleanToInt")
    default Integer booleanToInt(Boolean status) {
        if (status == null) {
            return null;
        }
        return status ? 1 : 0;
    }
}
