package net.maku.subcontrol.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订阅关系表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "订阅关系表")
public class FollowSubscribeOrderVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private Long id;

	@Schema(description = "喊单者的ID")
	private Long masterId;

	@Schema(description = "喊单者的账号")
	private Integer masterAccount;

	@Schema(description = "喊单者的订单号")
	private Integer masterTicket;

	@Schema(description = "喊单者开仓类型buy sell等")
	private Integer masterType;

	@Schema(description = "喊单者开仓时间(MT4时间)")
	private LocalDateTime masterOpenTime;

	@Schema(description = "侦测到开仓信号的时间")
	private LocalDateTime detectedOpenTime;

	@Schema(description = "喊单者平仓时间(MT4时间)")
	private LocalDateTime masterCloseTime;

	@Schema(description = "侦测到平仓信号的时间(系统时间)")
	private LocalDateTime detectedCloseTime;

	@Schema(description = "喊单者的开仓手数")
	private BigDecimal masterLots;

	@Schema(description = "喊单者的注释")
	private String comment;

	@Schema(description = "喊单者收益 swap commission profit的总和")
	private BigDecimal masterProfit;

	@Schema(description = "喊单者开仓的原始货币对")
	private String masterSymbol;

	@Schema(description = "跟单者的ID")
	private Long slaveId;

	@Schema(description = "跟单者账号")
	private Integer slaveAccount;

	@Schema(description = "跟单者对应的订单号，允许为null，为null时就是跟单没跟上")
	private Integer slaveTicket;

	@Schema(description = "跟单者开仓类型buy sell等")
	private Integer slaveType;

	@Schema(description = "跟单者开仓时间(MT4时间)")
	private LocalDateTime slaveOpenTime;

	@Schema(description = "跟单者收到喊单者开仓信号的时间")
	private LocalDateTime slaveReceiveTime;

	@Schema(description = "跟单者平仓时间(MT4时间)")
	private LocalDateTime slaveCloseTime;

	@Schema(description = "跟单者平仓时间(MT4时间)")
	private LocalDateTime slaveReceiveCloseTime;

	@Schema(description = "跟单者的开仓手数")
	private BigDecimal slaveLots;

	@Schema(description = "跟随者收益 swap commission profit的总和")
	private BigDecimal slaveProfit;

	@Schema(description = "跟单者开仓价格")
	private BigDecimal slaveOpenPrice;

	@Schema(description = "跟单者开仓的原始货币对")
	private String slaveSymbol;

	@Schema(description = "跟单者注释")
	private String slaveComment;

	@Schema(description = "下单模式0-固定手数 1-手数比例 2-净值比例")
	private Integer followMode;

	@Schema(description = "下单模式参数")
	private BigDecimal followParam;

	@Schema(description = "跟单者跟单方向")
	private Integer direction;

	@Schema(description = "类型0-信号源 1-跟单者")
	private Integer masterOrSlave;

	@Schema(description = "附加信息")
	private String extra;

	@Schema(description = "标记信息")
	private Integer flag;

	@Schema(description = "版本号")
	private Integer version;

	@Schema(description = "删除标识  0：正常   1：已删除")
	private Integer deleted;

	@Schema(description = "创建者")
	private Long creator;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "更新者")
	private Long updater;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

}