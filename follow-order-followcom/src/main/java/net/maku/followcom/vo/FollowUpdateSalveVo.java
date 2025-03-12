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
@Schema(description = "修改跟单账号")
public class FollowUpdateSalveVo implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "账号Id")
    @NotNull(message = "账号Id不能为空")
    private Long id;

    @Schema(description = "密码")
    @NotBlank(message = "密码不能为空")
    private String password;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "跟单方向0-正向1-反向")
    @Min(value = 0, message = "跟单方向参数不合法")
    @Max(value = 1, message = "跟单方向参数不合法")
    @NotNull(message = "跟单方向不能为空")
    private Integer followDirection;

    @Schema(description = "跟随模式0-固定手数 1-手数比例 2-净值比例")
    @Min(value = 0, message = "跟随模式参数不合法")
    @Max(value = 2, message = "跟随模式参数不合法")
    @NotNull(message = "跟随模式不能为空")
    private Integer followMode;

    @Schema(description = "跟单参数")
    private BigDecimal followParam;

    @Schema(description = "跟单状态0-未开启 1-已开启")
    @Min(value = 0, message = "跟单状态参数不合法")
    @Max(value = 1, message = "跟单状态参数不合法")
    @NotNull(message = "跟单状态不能为空")
    private Integer followStatus;

    @Schema(description = "跟单开仓状态 0-未开启 1-开启")
    @Min(value = 0, message = "跟单开仓状态参数不合法")
    @Max(value = 1, message = "跟单开仓状态参数不合法")
    @NotNull(message = "跟单开仓状态不能为空")
    private Integer followOpen;

    @Schema(description = "跟单平仓状态 0-未开启 1-开启")
    @Min(value = 0, message = "跟单平仓状态参数不合法")
    @Max(value = 1, message = "跟单平仓状态参数不合法")
    @NotNull(message = "跟单平仓状态不能为空")
    private Integer followClose;

    @Schema(description = "跟单补单状态 0-未开启 1-开启")
    @Min(value = 0, message = "跟单补单状态参数不合法")
    @Max(value = 1, message = "跟单补单状态参数不合法")
    @NotNull(message = "跟单补单状态不能为空")
    private Integer followRep;

    @Schema(description = "下单方式")
    @NotNull(message = "下单方式不能为空")
    private Integer placedType;

    private Long slaveAccount;

    @Schema(description = "模板ID")
    private Integer templateId;

    @Schema(description = "手数取余")
    @NotNull(message = "手数取余不能为空")
    @Min(value = 0, message = "手数取余参数不合法")
    @Max(value = 1, message = "手数取余参数不合法")
    private Integer remainder;

    @Schema(description = "固定注释")
    private String fixedComment;

    @Schema(description = "注释类型0-英文 1-数字 2-英文+数字+符号")
    private Integer commentType;

    @Schema(description = "位数")
    private Integer digits;
    @Schema(description = "forex")
    private String forex;
    @Schema(description = "cfd")
    private String cfd;
}
