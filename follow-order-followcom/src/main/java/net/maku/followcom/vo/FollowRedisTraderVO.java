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

	@Schema(description = "已用预付款")
	private  Double margin;

	@Schema(description = "服务器")
	private  String 	connectTrader;

	@Schema(description = "信用")
	private Double credit;

	@Schema(description = "跟单账号数量")
	private Integer slaveNum;

	@Schema(description = "账户")
	private String account;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "平台")
	private String platform;

	@Schema(description = "杠杆")
	private Integer leverage;

	@Schema(description = "下单模式")
	private String followMode;

	@Schema(description = "下单方式")
	private String placedType;

	@Schema(description = "连接状态")
	private Integer connectionStatus;

	@Schema(description = "跟单状态")
	private Integer followStatus;
}