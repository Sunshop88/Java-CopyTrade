package net.maku.followcom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 交易下单
 */
@Data
@Schema(description = "下单记录")
public class MasToSubOrderSendDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "品种类型")
	@NotBlank(message = "品种类型不能为空")
	private String symbol;

	@Schema(description = "账号Id")
	@NotNull(message = "账号id不能为空")
	private Long traderId;

	@Schema(description = "类型0-buy 1-sell")
	@NotNull(message = "类型不能为空")
	@Min(value = 0, message = "类型只能为0或1")
	@Max(value = 1, message = "类型只能为0或1")
	private Integer type;

	@Schema(description = "总单数")
	private Integer totalNum;

	@Schema(description = "总手数")
	private BigDecimal totalSzie;

	@Schema(description = "开始手数范围from")
	@NotNull(message = "开始手数范围不能为空")
	@DecimalMin(value = "0.01", message = "手数大于0.01")
	private BigDecimal startSize;

	@Schema(description = "结束手数范围to")
	@NotNull(message = "结束手数范围不能为空")
	@DecimalMin(value = "0.01", message = "手数大于0.01")
	private BigDecimal endSize;

	@Schema(description = "间隔时间 毫秒")
	private Integer intervalTime;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "交易类型")
	private Integer tradeType;

	@Schema(description = "订单号")
	private String sendNo;

}