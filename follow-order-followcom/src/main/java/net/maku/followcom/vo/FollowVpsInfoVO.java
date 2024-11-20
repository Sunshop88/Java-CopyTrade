package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * vps信息
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "vps信息")
public class FollowVpsInfoVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "总数")
    private Integer total;

    @Schema(description = "对外开放数量")
    private Integer openNum;

    @Schema(description = "运行中数量")
    private Integer runningNum;

    @Schema(description = "关闭数量")
    private Integer closeNum;

    @Schema(description = "异常数量")
    private Integer errorNum;

    @Schema(description = "策略正常数量")
    private Integer masterSuccess;

    @Schema(description = "策略总数量")
    private Integer masterTotal;

    @Schema(description = "跟单正常数量")
    private Integer slaveSuccess;

    @Schema(description = "跟单总数量")
    private Integer slaveTotal;

    @Schema(description = "总持仓单量")
    private Integer orderCountTotal;

    @Schema(description = "总持仓手数")
    private BigDecimal orderLotsTotal;

    @Schema(description = "总净值")
    private BigDecimal orderEquityTotal;
	
    @Schema(description = "总盈亏")
    private BigDecimal orderProfitTotal;

}