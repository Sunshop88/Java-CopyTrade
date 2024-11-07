package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Schema(description = "修改跟单账号")
public class FollowUpdateSalveVo implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "账号Id")
    private Long id;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "跟单方向0-正向1-反向")
    private Integer followDirection;

    @Schema(description = "跟随模式0-固定手数 1-手数比例 2-净值比例")
    private Integer followMode;

    @Schema(description = "跟单参数")
    private BigDecimal followParam;

    @Schema(description = "跟单状态0-未开启 1-已开启")
    private Integer followStatus;

    @Schema(description = "跟单开仓状态 0-未开启 1-开启")
    private Integer followOpen;

    @Schema(description = "跟单平仓状态 0-未开启 1-开启")
    private Integer followClose;

    @Schema(description = "跟单补单状态 0-未开启 1-开启")
    private Integer followRep;

    @Schema(description = "下单方式")
    private Integer placedType;

    private Long slaveAccount;

}
