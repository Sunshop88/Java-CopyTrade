package net.maku.followcom.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.io.Serializable;
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
	@Min(value = 0, message = "账号类型参数不合法")
	@Max(value = 2, message = "账号类型参数不合法")
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

	@Schema(description = "跟单状态0-未开启 1-已开启")
	private Integer followStatus;
	@Schema(description = "盈亏")
	private BigDecimal profit;

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

	@Schema(description = "模板ID")
	private Integer templateId;

	@Schema(description = "下单类型")
	private Integer placedType;


	/**
	 * 跟随模式0-固定手数 1-手数比例 2-净值比例
	 */
	private Integer followMode;


	/**
	 * 跟单开仓状态 0-未开启 1-开启
	 */
	private Integer followOpen;

	/**
	 * 跟单平仓状态 0-未开启 1-开启
	 */
	private Integer followClose;

	/**
	 * 跟单补单状态 0-未开启 1-开启
	 */
	private Integer followRep;


	/**
	 * 跟单比例
	 */
	private BigDecimal followParam;

	/**
	 * 跟单方向0-正向1-反向
	 */
	@TableField(value = "follow_direction")
	private Integer followDirection;

	private Integer remainder;
    //已用预付款
	private  Double margin;
	//服务器
	private  String 	connectTrader;
	//信用
	private Double credit;

	@Schema(description = "登录节点")
	private String loginNode;

	private String defaultServerAccount;



	@Schema(description = "固定注释")
	private String fixedComment;

	@Schema(description = "注释类型0-英文 1-数字 2-英文+数字+符号")
	private Integer commentType;

	@Schema(description = "位数")
	private Integer digits;

	@Schema(description = "新密码")
	private String newPassword;
	//用于挂号vps(true 需要新增)
	private Boolean isAdd;
}