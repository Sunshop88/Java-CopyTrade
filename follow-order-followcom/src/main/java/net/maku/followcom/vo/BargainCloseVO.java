package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * Author:  zsd
 * Date:  2025/2/28/周五 14:15
 */
@Data
public class BargainCloseVO implements Serializable {
    @Schema(description = "品种类型")
    //@NotBlank(message = "品种不能为空")
    private String symbol;

    @Schema(description = "类型0-buy 1-sell")
    @Min(value =0, message = "订单方向参数不合法")
    @Max(value =2, message = "订单方向参数不合法")
    // @NotNull(message = "订单方向不能为空")
    private Integer type;

    @Schema(description = "总单数")
    @Min(value =1, message = "总单数最少一单")
    private Integer num;

    @Schema(description = "间隔时间 毫秒")
    private Integer intervalTime;

    @Schema(description = "是否全平")
    //@NotNull(message = "是否全平不能为空")
    private Integer flag;

    @Schema(description = "订单号")
    private Integer orderNo;

    @Schema(description = "手数")
    private double size;

    @Schema(description = "mt4全平1全平0或者空不是")
    private Integer isCloseAll;

    @Schema(description = "mt4全平 0-盈利 1-亏损")
    private Integer profitOrLoss;
    //账号列表id
    private Long traderUserId;
}
