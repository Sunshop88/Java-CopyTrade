package net.maku.mascontrol.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 下单进度
 */
@Data
@Schema(description = "下单进度")
public class FollowBarginOrderVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "sell价格")
	private double sellPrice;

	@Schema(description = "buy价格")
	private double buyPrice;

	@Schema(description = "进度数量")
	private Integer scheduleNum;

	@Schema(description = "进度成功")
	private Integer scheduleSuccessNum;

	@Schema(description = "进度失败")
	private Integer scheduleFailNum;

	@Schema(description = "状态0-进行中 1-已完成")
	private Integer status;

	@Schema(description = "订单号")
	private String orderNo;
}