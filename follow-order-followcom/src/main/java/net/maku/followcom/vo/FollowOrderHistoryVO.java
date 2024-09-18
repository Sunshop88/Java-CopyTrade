package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import lombok.Data;
import java.io.Serializable;
import net.maku.framework.common.utils.DateUtils;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 所有MT4账号的历史订单
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "所有MT4账号的历史订单")
public class FollowOrderHistoryVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "id")
	private String id;

	@Schema(description = "订单")
	private Integer orderNo;

	@Schema(description = "类型0-buy 1-sell 6-balance 7-credit")
	private Integer type;

	@Schema(description = "开仓时间")
	private Date openTime;

	@Schema(description = "平仓时间")
	private Date closeTime;

	@Schema(description = "手数")
	private BigDecimal size;

	@Schema(description = "交易品种")
	private String symbol;

	@Schema(description = "开仓价格")
	private BigDecimal openPrice;

	@Schema(description = "平仓价格")
	private BigDecimal closePrice;

	@Schema(description = "止损")
	private BigDecimal sl;

	@Schema(description = "止盈")
	private BigDecimal tp;

	@Schema(description = "手续费")
	private BigDecimal commission;

	@Schema(description = "税费")
	private BigDecimal taxes;

	@Schema(description = "库存费")
	private BigDecimal swap;

	@Schema(description = "获利")
	private BigDecimal profit;

	@Schema(description = "注释")
	private String comment;

	@Schema(description = "魔数")
	private Integer magic;

	@Schema(description = "写库时间")
	private Integer realTime;

	@Schema(description = "MT4账号id")
	private String traderId;

	@Schema(description = "MT4账号")
	private String account;

	@Schema(description = "盈利点数")
	private Integer profitPoint;

	@Schema(description = "版本号")
	private Integer version;

	@Schema(description = "删除标识  0：正常   1：已删除")
	private Integer deleted;

	@Schema(description = "创建者")
	private Long creator;

	@Schema(description = "创建时间")
	private Date createTime;

	@Schema(description = "更新者")
	private Long updater;

	@Schema(description = "更新时间")
	private Date updateTime;

}