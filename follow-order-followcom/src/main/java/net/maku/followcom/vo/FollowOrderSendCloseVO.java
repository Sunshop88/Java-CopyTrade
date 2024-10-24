package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 平仓
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "平仓")
public class FollowOrderSendCloseVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;

	@Schema(description = "品种类型")
	private String symbol;

	@Schema(description = "账号id")
	private Long traderId;

	@Schema(description = "账号")
	private String account;

	@Schema(description = "类型0-buy 1-sell")
	private Integer type;

	@Schema(description = "总单数")
	private Integer num;

	@Schema(description = "间隔时间 毫秒")
	private Integer intervalTime;

	@Schema(description = "是否全平")
	private Integer flag;

	@Schema(description = "订单号")
	private Integer orderNo;

	@Schema(description = "手数")
	private double size;


}