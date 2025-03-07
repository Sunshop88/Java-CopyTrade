package net.maku.mascontrol.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 下单子指令
 */
@Data
@Schema(description = "下单子指令")
public class FollowBaiginInstructSubVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "账号类型")
	private String traderType="MT4";

	@Schema(description = "类型")
	private Integer type;

	@Schema(description = "品种")
	private String symbol;

	@Schema(description = "账号")
	private String account;

	@Schema(description = "服务器")
	private String server;

	@Schema(description = "手数")
	private BigDecimal size;

	@Schema(description = "状态")
	private String statusComment;

}