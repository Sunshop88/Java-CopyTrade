package net.maku.mascontrol.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
	private String platform;

	@Schema(description = "手数")
	private BigDecimal size;

	@Schema(description = "状态")
	private String statusComment;

	@Schema(description = "指令发起时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime createTime;

	@Schema(description = "指令成交时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime responseOpenTime;


}