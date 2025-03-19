package net.maku.subcontrol.vo;

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
 * 补单记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "补单记录")
public class FollowRepairOrderVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;

	@Schema(description = "类型0-下单 1-平仓")
	private Integer type;

	@Schema(description = "喊单者的ID")
	private Long masterId;

	@Schema(description = "喊单者账号")
	private String masterAccount;

	@Schema(description = "喊单者的订单号")
	private Integer masterTicket;

	@Schema(description = "喊单者开仓时间")
	private LocalDateTime masterOpenTime;

	@Schema(description = "喊单者开仓的原始货币对")
	private String masterSymbol;

	@Schema(description = "喊单者的开仓手数")
	private BigDecimal masterLots;

	@Schema(description = "跟单者的ID")
	private Long slaveId;

	@Schema(description = "跟单者的账号")
	private String slaveAccount;

	@Schema(description = "订阅ID")
	private Long subscribeId;

	@Schema(description = "状态0-未处理 1-已处理")
	private Integer status;

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

	//跟单单号
	private Integer salveTicket;

	//跟单开仓时间
	private LocalDateTime slaveOpenTime;

	//跟单者开仓的原始货币对
	private String slaveSymbol;

	//跟单者的开仓手数
	private BigDecimal slaveLots;

	//喊单者的盈亏金额
	private BigDecimal masterProfit;

	//跟单者的盈亏金额
	private BigDecimal slaveProfit;

}