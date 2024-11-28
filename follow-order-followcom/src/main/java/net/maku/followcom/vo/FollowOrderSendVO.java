package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 下单记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "下单记录")
public class FollowOrderSendVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;

	@Schema(description = "品种类型")
	@NotBlank(message = "品种类型不能为空")
	private String symbol;

	@Schema(description = "券商")
	private String brokeName;

	@Schema(description = "服务器")
	private String platform;

	@Schema(description = "账号id")
	@NotNull(message = "账号id不能为空")
	private Long traderId;

	@Schema(description = "账号")
	private String account;

	@Schema(description = "类型0-buy 1-sell")
	@NotNull(message = "类型不能为空")
	@Min(value = 0, message = "类型只能为0或1")
	@Max(value = 1, message = "类型只能为0或1")
	private Integer type;

	@Schema(description = "总单数")
	private Integer totalNum;

	@Schema(description = "成功单数")
	private Integer successNum;

	@Schema(description = "失败单数")
	private Integer failNum;

	@Schema(description = "总手数")
	private BigDecimal totalSzie;

	@Schema(description = "实际下单手数")
	private BigDecimal trueSzie;

	@Schema(description = "开始手数范围from")
	@NotNull(message = "开始手数范围不能为空")
	@DecimalMin(value = "0.01", message = "手数大于0.01")
	private BigDecimal startSize;

	@Schema(description = "结束手数范围to")
	@NotNull(message = "结束手数范围不能为空")
	@DecimalMin(value = "0.01", message = "手数大于0.01")
	private BigDecimal endSize;

	@Schema(description = "状态0-进行中 1-已完成")
	private Integer status;

	@Schema(description = "间隔时间 毫秒")
	private Integer intervalTime;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "创建者")
	private Long creator;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "订单号")
	private String orderNo;

	@Schema(description = "下单方式")
	private Integer placedType;

	@Schema(description = "服务器")
	private String server;

	@Schema(description = "vps地址")
	private String ipAddr;

	@Schema(description = "vps名称")
	private String serverName;

}