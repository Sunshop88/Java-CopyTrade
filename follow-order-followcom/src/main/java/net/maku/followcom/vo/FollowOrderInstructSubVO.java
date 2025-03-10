package net.maku.followcom.vo;

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
 * 下单子指令
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "下单子指令")
public class FollowOrderInstructSubVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;

	@Schema(description = "总指令id")
	private String sendNo;

	@Schema(description = "账号")
	private String account;

	@Schema(description = "账号类型 MT4或MT5")
	private String accountType;

	@Schema(description = "品种")
	private String symbol;

	@Schema(description = "服务器")
	private String platform;

	@Schema(description = "类型 0-buy 1-sell")
	private Integer type;

	@Schema(description = "状态0-成功1-各类错误")
	private Integer status;

	@Schema(description = "状态描述")
	private String statusComment;

	@Schema(description = "手数")
	private BigDecimal lots;

	@Schema(description = "指令结束时间")
	private LocalDateTime endTime;

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