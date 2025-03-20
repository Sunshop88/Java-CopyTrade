package net.maku.followcom.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 持仓订单
 */
@Data
@Schema(description = "持仓订单")
public class OrderActiveInfoVO implements Serializable {

	@Schema(description = "订单号")
	private Integer orderNo;

	@Schema(description = "账号")
	private String account;

	@Schema(description = "开仓时间")
	private LocalDateTime openTime;

	@Schema(description = "品种")
	private String symbol;

	@Schema(description = "手数")
	private double lots;

	@Schema(description = "开仓价格")
	private double openPrice;

	@Schema(description = "止盈")
	private double takeProfit;

	@Schema(description = "止损")
	private double stopLoss;

	@Schema(description = "手续费")
	private double commission;

	@Schema(description = "利息")
	private double swap;

	@Schema(description = "盈亏")
	private double profit;

	@Schema(description = "下单类型")
	private String type;

	@Schema(description = "魔术号")
	private Integer magicNumber;

	@Schema(description = "备注")
	private String comment;

	@Schema(description = "价格滑点")
	private BigDecimal priceSlip;
	/**
	 * 开仓请求时间
	 */
	private Integer openTimeDifference;

	/**
	 * 开仓价格差
	 */
	private BigDecimal openPriceDifference;


	/**
	 * 开仓价格滑点
	 */
	private BigDecimal openPriceSlip;

	public double rateMargin;



}