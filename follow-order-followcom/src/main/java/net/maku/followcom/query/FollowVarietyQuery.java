package net.maku.followcom.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;

/**
 * 品种匹配查询
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "品种匹配查询")
public class FollowVarietyQuery extends Query {
    private String StdSymbol;
    @NotNull(message = "模板类型不能为空")
    private Integer template;
}