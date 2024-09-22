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
 * 订单详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "订单详情")
public class FollowOrderDetailVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;

	@Schema(description = "品种")
	private String symbol;

	@Schema(description = "订单号")
	private Integer orderNo;

	@Schema(description = "账号id")
	private Long traderId;

	@Schema(description = "账号")
	private String account;

	@Schema(description = "开仓请求时间")
	private Date requestTime;

	@Schema(description = "开仓请求价格")
	private BigDecimal requestPrice;

	@Schema(description = "开仓时间")
	private Date openTime;

	@Schema(description = "开仓价格")
	private BigDecimal openPrice;

	@Schema(description = "开仓价格滑点")
	private BigDecimal openPriceSlip;

	@Schema(description = "手数")
	private BigDecimal size;

	@Schema(description = "止盈")
	private BigDecimal tp;

	@Schema(description = "止损")
	private BigDecimal sl;

	@Schema(description = "手续费")
	private BigDecimal commission;

	@Schema(description = "利息")
	private BigDecimal swap;

	@Schema(description = "版本号")
	private Integer version;

	@Schema(description = "删除标识 0：正常 1：已删除")
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