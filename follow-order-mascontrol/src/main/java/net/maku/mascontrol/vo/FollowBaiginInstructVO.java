package net.maku.mascontrol.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 下单指令
 */
@Data
@Schema(description = "下单指令")
public class FollowBaiginInstructVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "指令类型0-分配 1-复制")
	private Integer instructionType;

	@Schema(description = "品种")
	private String symbol;

	@Schema(description = "类型")
	private Integer type;

	@Schema(description = "下单总手数")
	private BigDecimal trueTotalLots;

	@Schema(description = "下单总手数")
	private Integer trueTotalOrders;

	@Schema(description = "成交手数")
	private BigDecimal tradedLots;

	@Schema(description = "成交单数")
	private Integer tradedOrders;

	@Schema(description = "状态0-执行中 1-全部成功 2-存在失败")
	private Integer status;

	@Schema(description = "子指令")
	private List<FollowBaiginInstructSubVO> followBaiginInstructSubVOList;

}