package net.maku.subcontrol.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;

/**
 * 所有MT4账号的历史订单查询
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "所有MT4账号的历史订单查询")
public class FollowOrderHistoryQuery extends Query {

    @Schema(description = "账号ID")
    private Long traderId;
}