package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Author:  zsd
 * Date:  2025/2/27/周四 10:34
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HangVpsVO {

    //账号列表
    @Min(value = 1, message = "请选择挂靠账号")
    private List<Long>  traderUserIds;
    //账号类型
    @Schema(description = "账号类型  1跟单 2交易分配")
    @NotEmpty(message = "挂靠类型不能为空")
    private Integer accountType;
    @NotEmpty(message = "挂靠vps不能为空")
    private Integer vpsId;

    @Schema(description = "喊单账号")
    private Long traderId;

    @Schema(description = "跟单方向0-正向1-反向")
    @Min(value = 0, message = "跟单方向只能为0或1")
    @Max(value = 1, message = "跟单方向只能为0或1")
    @NotNull(message = "跟单方向不能为空")
    private Integer followDirection;

    @Schema(description = "跟随模式0-固定手数 1-手数比例 2-净值比例")
    @Min(value = 0, message = "跟随模式只能为0、1或2")
    @Max(value = 2, message = "跟随模式只能为0、1或2")
    @NotNull(message = "跟随模式不能为空")
    private Integer followMode;
    @Schema(description = "参数比例")
    private BigDecimal followParam;
    @Schema(description = "手数取余")
    @NotNull(message = "手数取余不能为空")
    @Min(value = 0, message = "手数取余参数不合法")
    @Max(value = 1, message = "手数取余参数不合法")
    private Integer remainder;

    @Schema(description = "下单类型")
    private Integer placedType;

    @Schema(description = "模板ID")
    @NotNull(message = "模板ID不能为空")
    private Integer templateId;


    @Schema(description = "跟单状态0-未开启 1-已开启")
    @Min(value = 0, message = "跟单状态只能为0或1")
    @Max(value = 1, message = "跟单状态只能为0或1")
    @NotNull(message = "跟单状态0不能为空")
    private Integer followStatus;

    @Schema(description = "跟单开仓状态 0-未开启 1-开启")
    @Min(value = 0, message = "跟单开仓状态只能为0或1")
    @Max(value = 1, message = "跟单开仓状态只能为0或1")
    @NotNull(message = "跟单开仓状态不能为空")
    private Integer followOpen;

    @Schema(description = "跟单平仓状态 0-未开启 1-开启")
    @Min(value = 0, message = "跟单平仓状态只能为0或1")
    @Max(value = 1, message = "跟单平仓状态只能为0或1")
    @NotNull(message = "跟单平仓状态不能为空")
    private Integer followClose;


}
