package net.maku.followcom.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;
import org.ehcache.javadoc.PrivateApi;

/**
 * 品种规格查询
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "品种规格查询")
public class FollowSysmbolSpecificationQuery extends Query {
    @Schema(description = "Mt4账号id")
    private Long traderId;
    @Schema(description = "账号id")
    private Long traderUserId;
    @Schema(description = "品种")
    private String symbol;
    @Schema(description = "品种类型")
    private String profitMode;
}