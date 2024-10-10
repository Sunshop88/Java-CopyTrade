package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
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
	private String symbol;

	@Schema(description = "券商")
	private String brokeName;

	@Schema(description = "服务器")
	private String platform;

	@Schema(description = "账号id")
	private Long traderId;

	@Schema(description = "账号")
	private String account;

	@Schema(description = "类型0-buy 1-sell")
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
	private BigDecimal startSize;

	@Schema(description = "结束手数范围to")
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

}