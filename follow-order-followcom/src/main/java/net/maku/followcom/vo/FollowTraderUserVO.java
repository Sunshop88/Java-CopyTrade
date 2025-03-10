package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import net.maku.framework.common.utils.DateUtils;
import java.util.Date;

/**
 * 账号初始表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "账号初始表")
public class FollowTraderUserVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;
	@Schema(description = "挂号的vps")
	private List<VpsDescVO> vpsDesc;
	@Schema(description = "账号")
	@NotBlank(message = "账号不能为空")
	private String account;

	@Schema(description = "密码")
	@NotBlank(message = "密码不能为空")
	@Size(min = 6, max = 16, message = "密码长度应在6到16位之间")
	private String password;

	@Schema(description = "平台id")
	private Integer platformId;

	@Schema(description = "平台服务器")
	@NotBlank(message = "平台服务器不能为空")
	private String platform;

	@Schema(description = "账号类型 MT4或MT5")
	@NotBlank(message = "账号类型不能为空")
	private String accountType;

	@Schema(description = "服务器节点")
	@NotBlank(message = "服务器节点不能为空")
	private String serverNode;

	@Schema(description = "排序 默认：1")
	private Integer sort;

	@Schema(description = "组别名称")
	private String groupName;

	@Schema(description = "组别id")
	private Integer groupId;

	@Schema(description = "挂靠状态0-未挂靠 1-已挂靠")
	private Integer status;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "版本号")
	private Integer version;

	@Schema(description = "删除标识 0：正常 1：已删除")
	private Integer deleted;

	@Schema(description = "创建者")
	private Long creator;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "更新者")
	private Long updater;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	@Schema(description = "二次确认密码")
	private String confirmPassword;

	@Schema(description = "净值")
	private	BigDecimal euqit;
	@Schema(description = "余额")
	private	BigDecimal balance;
	@Schema(description = "款比例")
	private	BigDecimal marginProportion;
	@Schema(description = "可用预付款")
	private BigDecimal freeMargin;
	@Schema(description = "已用预付款")
	private  Double margin;
	@Schema(description = "总持仓订单数量")
	private Integer total;

	@Schema(description = "做空订单手数数量")
	private double sellNum;

	@Schema(description = "做多订单手数数量")
	private double buyNum;

	@Schema(description = "券商名称")
	private String brokerName;
	@Schema(description = "杠杆")
	private Integer leverage;
	@Schema(description = "组别颜色")
	private String  groupColor;


}