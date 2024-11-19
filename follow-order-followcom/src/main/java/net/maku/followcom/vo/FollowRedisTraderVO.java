package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账号信息缓存
 */
@Data
@Schema(description = "账号信息缓存")
public class FollowRedisTraderVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "账号id")
	private Long traderId;

	@Schema(description = "ip地址")
	private BigDecimal balance;

	@Schema(description = "净值")
	private BigDecimal euqit;

	@Schema(description = "预付款比例")
	private BigDecimal marginProportion;

	@Schema(description = "可用预付款")
	private BigDecimal freeMargin;

	@Schema(description = "总持仓订单数量")
	private Integer total;

	@Schema(description = "做空订单手数数量")
	private double sellNum;

	@Schema(description = "做多订单手数数量")
	private double buyNum;

	@Schema(description = "盈亏")
	private BigDecimal profit;

}