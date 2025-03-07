package net.maku.mascontrol.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 下单指令
 */
@Data
@Schema(description = "下单指令")
public class FollowBaiginInstructVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "指令类型")
	private String type;

	@Schema(description = "品种")
	private String symbol;

	@Schema(description = "类型")
	private Integer scheduleNum;

	@Schema(description = "下单")
	private Integer scheduleSuccessNum;

	@Schema(description = "下单总手数")
	private BigDecimal totalLots;

	@Schema(description = "下单总订单")
	private Integer trueOrders;

	@Schema(description = "成交手数")
	private BigDecimal tradedLots;

	@Schema(description = "成交单数")
	private Integer tradedOrders;

}