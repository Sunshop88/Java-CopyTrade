package net.maku.followcom.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.io.Serializable;
import net.maku.framework.common.utils.DateUtils;
import java.math.BigDecimal;

/**
 * mt4账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "mt4账号")
public class FollowTraderVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;

	@Schema(description = "mt4账号")
	@NotBlank(message = "mt4账号不能为空")
	private String account;

	@Schema(description = "类型0-信号源 1-跟单者")
	@Min(value = 0, message = "类型只能为0或1")
	@Max(value = 1, message = "类型只能为0或1")
	@NotNull(message = "账号类型不能为空")
	private Integer type;

	@Schema(description = "密码")
	@NotBlank(message = "密码不能为空")
	private String password;

	@Schema(description = "平台id")
	private Integer platformId;

	@Schema(description = "平台服务器")
	@NotBlank( message = "平台服务器不能为空")
	private String platform;

	@Schema(description = "状态0-正常 1-异常")
	private Integer status;

	@Schema(description = "异常信息")
	private String statusExtra;

	@Schema(description = "服务器ip")
	private String ipAddr;

	@Schema(description = "服务器id")
	private String serverId;

	@Schema(description = "服务器名称")
	private String serverName;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "净值")
	private BigDecimal euqit;

	@Schema(description = "余额")
	private BigDecimal balance;

	@Schema(description = "可用预付款")
	private BigDecimal freeMargin;

	@Schema(description = "预付款比例")
	private BigDecimal marginProportion;

	@Schema(description = "杠杆")
	private Integer leverage;

	@Schema(description = "倍数")
	private BigDecimal multiple;

	@Schema(description = "是否demo")
	private Boolean isDemo;

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

	@Schema(description = "总持仓订单数量")
	private Integer total;

	@Schema(description = "做空订单手数数量")
	private double sellNum;

	@Schema(description = "做多订单手数数量")
	private double buyNum;

	private String serverIp;

}