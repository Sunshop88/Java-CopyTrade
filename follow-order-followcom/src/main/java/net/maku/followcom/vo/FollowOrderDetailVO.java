package net.maku.followcom.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
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
public class FollowOrderDetailVO  implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;

	@Schema(description = "品种")
	private String symbol;

	@Schema(description = "类型")
	private Integer type;

	@Schema(description = "订单号")
	private Integer orderNo;

	@Schema(description = "账号id")
	private Long traderId;

	@Schema(description = "账号")
	private String account;

	@Schema(description = "开仓请求时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
	private LocalDateTime requestOpenTime;

	@Schema(description = "开仓请求价格")
	private BigDecimal requestOpenPrice;

	@Schema(description = "开仓时间")
	private LocalDateTime openTime;

	@Schema(description = "开仓价格")
	private BigDecimal openPrice;

	@Schema(description = "开仓价格滑点")
	private BigDecimal openPriceSlip;

	@Schema(description = "平仓请求时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
	private LocalDateTime requestCloseTime;

	@Schema(description = "平仓请求价格")
	private BigDecimal requestClosePrice;

	@Schema(description = "平仓时间")
	private LocalDateTime closeTime;

	@Schema(description = "平仓价格")
	private BigDecimal closePrice;

	@Schema(description = "平仓价格滑点")
	private BigDecimal closePriceSlip;

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

	@Schema(description = "下单号")
	private String sendNo;

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

	@Schema(description = "异常信息")
	private String remark;

	@Schema(description = "开仓时间差 毫秒")
	private Integer openTimeDifference;

	@Schema(description = "平仓时间差 毫秒")
	private Integer closeTimeDifference;

	@Schema(description = "开仓响应时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
	private LocalDateTime responseOpenTime;

	@Schema(description = "平仓响应时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
	private LocalDateTime responseCloseTime;

	@Schema(description = "盈亏")
	private BigDecimal profit;

	@Schema(description = "券商")
	private String brokeName;

	@Schema(description = "服务器")
	private String platform;

	@Schema(description = "下单方式")
	private Integer placedType;

	@Schema(description = "vps地址")
	private String ipAddr;

	@Schema(description = "vps名称")
	private String serverName;
	/**
	 * 平仓状态
	 */
	@Schema(description = "平仓状态")
	private Integer closeStatus;

	//喊单账号
	@Schema(description = "喊单账号")
	private  String sourceUser;

	//结算汇率
	@Schema(description = "结算汇率")
	private  String rateMargin;

	//魔术号
	@Schema(description = "魔术号")
	private  String magical;

	//节点地址
	@Schema(description = "节点地址")
	private  String serverHost;

	/**
	 * 平仓VPS
	 */
	@Schema(description = "平仓VPS")
	private String closeServerName;

	/**
	 * 平仓VPS地址
	 */
	@Schema(description = "平仓VPS地址")
	private  String closeIpAddr;

	/**
	 * 平仓节点
	 */
	@Schema(description = "平仓节点")
	private  String closeServerHost;

	@Schema(description = "是否外部")
	private Integer isExternal;
	/***
	 * MT4备注
	 * */
	@Schema(description = " MT4备注")
	private String comment;



}