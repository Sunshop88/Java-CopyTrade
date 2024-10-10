package net.maku.followcom.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;

import java.util.List;

/**
 * 滑点分析列表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "滑点分析列表")
public class FollowOrderSpliListQuery extends Query {

    @Schema(description = "账户id")
    private String traderId;

    @Schema(description = "账户")
    private String account;

    @Schema(description = "平台名称")
    private String platform;

    @Schema(description = "品种名称")
    private String symbol;

    @Schema(description = "开仓时间-start")
    private String startTime;

    @Schema(description = "开仓时间-end")
    private String endTime;

    private List<String> symbolList;
    private List<String> traderIdList;
}