package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Schema(description = "添加跟单账号")
public class FollowAddSalveVo  implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "喊单账号")
    @NotBlank(message = "喊单账号不能为空")
    private Long traderId;

    @Schema(description = "平台")
    @NotBlank(message = "平台不能为空")
    private String platform;

    @Schema(description = "账号")
    @NotBlank(message = "账号不能为空")
    private String account;

    @Schema(description = "密码")
    @NotBlank(message = "密码不能为空")
    private String password;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "跟单方向0-正向1-反向")
    @Min(value = 0, message = "跟单方向只能为0或1")
    @Max(value = 1, message = "跟单方向只能为0或1")
    @NotBlank(message = "平台不能为空")
    private Integer followDirection;

    @Schema(description = "跟随模式0-固定手数 1-手数比例 2-净值比例")
    @Min(value = 0, message = "跟随模式只能为0、1或2")
    @Max(value = 2, message = "跟随模式只能为0、1或2")
    @NotBlank(message = "跟随模式不能为空")
    private Integer followMode;

    @Schema(description = "跟单参数")
    private BigDecimal followParam;

    @Schema(description = "跟单状态0-未开启 1-已开启")
    @Min(value = 0, message = "跟单状态只能为0或1")
    @Max(value = 1, message = "跟单状态只能为0或1")
    @NotBlank(message = "跟单状态0不能为空")
    private Integer followStatus;

    @Schema(description = "跟单开仓状态 0-未开启 1-开启")
    @Min(value = 0, message = "跟单开仓状态只能为0或1")
    @Max(value = 1, message = "跟单开仓状态只能为0或1")
    @NotBlank(message = "跟单开仓状态不能为空")
    private Integer followOpen;

    @Schema(description = "跟单平仓状态 0-未开启 1-开启")
    @Min(value = 0, message = "跟单平仓状态只能为0或1")
    @Max(value = 1, message = "跟单平仓状态只能为0或1")
    @NotBlank(message = "跟单平仓状态不能为空")
    private Integer followClose;

    @Schema(description = "跟单补单状态 0-未开启 1-开启")
    @Min(value = 0, message = "跟单补单状态只能为0或1")
    @Max(value = 1, message = "跟单补单状态只能为0或1")
    @NotBlank(message = "跟单补单状态不能为空")
    private Integer followRep;

    @Schema(description = "下单方式")
    private Integer placedType;

    private Long slaveId;

    private String slaveAccount;

    private String masterAccount;

    @Schema(description = "模板ID")
    @NotNull(message = "模板ID不能为空")
    private Integer templateId;

}
