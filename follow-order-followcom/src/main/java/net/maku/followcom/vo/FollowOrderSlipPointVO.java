package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 滑点分析
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "滑点分析")
public class FollowOrderSlipPointVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "账户id")
	private long traderId;

	@Schema(description = "券商")
	private String brokeName;

	@Schema(description = "服务器")
	private String platform;

	@Schema(description = "账号")
	private String account;

	@Schema(description = "总订单数")
	private Integer totalNum;

	@Schema(description = "品种")
	private String symbol;

	@Schema(description = "品种单数")
	private Integer symbolNum;

	@Schema(description = "开仓平均时间差")
	private BigDecimal meanOpenTimeDifference;

	@Schema(description = "开仓平均价格差")
	private BigDecimal meanOpenPriceDifference;

	@Schema(description = "平仓平均时间差")
	private BigDecimal meanCloseTimeDifference;

	@Schema(description = "平仓平均价格差")
	private BigDecimal meanClosePriceDifference;

	@Schema(description = "开仓平均滑点")
	private BigDecimal meanOpenPriceSlip;

	@Schema(description = "平仓平均滑点")
	private BigDecimal meanClosePriceSlip;

	//喊单账号
	private  String sourceUser;
	//结算汇率
	private  String rateMargin;
	//魔术号
	private  String magical;
	//ip
	private String ipAddr;
}