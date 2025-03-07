package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "交易下单数据")
public class FollowMasOrderVo implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "订单总单数")
    private Integer totalNum;

    @Schema(description = "订单总手数")
    private double totalSize;

    @Schema(description = "下单详情")
    private List<Double> orderList;
}
