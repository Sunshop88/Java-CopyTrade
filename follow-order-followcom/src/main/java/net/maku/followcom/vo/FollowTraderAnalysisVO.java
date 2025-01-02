package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import lombok.Data;
import java.io.Serializable;
import net.maku.framework.common.utils.DateUtils;
import java.math.BigDecimal;

/**
 * 账号数据分析表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "账号数据分析表")
public class FollowTraderAnalysisVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "mt4账号")
	private String account;

	@Schema(description = "vpsId")
	private Long vpsId;

	@Schema(description = "品种")
	private String symbol;

	@Schema(description = "vps名称")
	private String vpsName;

	@Schema(description = "平台服务器")
	private String platform;

	@Schema(description = "平台服务器id")
	private Integer platformId;

	@Schema(description = "信号源账号")
	private String sourceAccount;

	@Schema(description = "信号源服务器")
	private String sourcePlatform;

	@Schema(description = "净头寸")
	private BigDecimal position;

	@Schema(description = "总持仓手数")
	private BigDecimal lots;

	@Schema(description = "总订单数")
	private BigDecimal num;

	@Schema(description = "总盈利")
	private BigDecimal profit;

	@Schema(description = "多仓订单量")
	private BigDecimal buyNum;

	@Schema(description = "多仓手数")
	private BigDecimal buyLots;

	@Schema(description = "多仓盈利")
	private BigDecimal buyProfit;

	@Schema(description = "空仓订单量")
	private BigDecimal sellNum;

	@Schema(description = "空仓手数")
	private BigDecimal sellLots;

	@Schema(description = "空仓盈利")
	private BigDecimal sellProfit;

}