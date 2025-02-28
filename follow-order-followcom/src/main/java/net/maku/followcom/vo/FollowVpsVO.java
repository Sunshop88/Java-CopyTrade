package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * vps列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "vps列表")
public class FollowVpsVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Integer id;

	@Schema(description = "名称")
	@NotBlank(message = "名称不为空")
	private String name;

	@Schema(description = "机器码")
	private String clientId;

	@Schema(description = "ip地址")
	@NotBlank(message = "ip地址不为空")
	private String ipAddress;

	@Schema(description = "到期时间")
	@NotNull(message = "到期时间不为空")
	private LocalDateTime expiryDate;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "是否对外开放，0为否，1为是")
	@Min(value = 0, message = "是否对外开放只能为0或1")
	@Max(value = 1, message = "是否对外开放只能为0或1")
	private Integer isOpen;

	@Schema(description = "是否状态，0为停止，1为运行")
	@Min(value = 0, message = "状态只能为0或1")
	@Max(value = 1, message = "状态只能为0或1")
	private Integer isActive;

	@Schema(description = "连接状态，0为异常，1为正常")
	@Min(value = 0, message = "连接状态只能为0或1")
	@Max(value = 1, message = "连接状态只能为0或1")
	private Integer connectionStatus;

	@Schema(description = "是否同步，0否，1是")
	@Min(value = 0, message = "状态只能为0或1")
	@Max(value = 1, message = "状态只能为0或1")
	private Integer isSyn;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "剩余到期天数")
	private Integer remainingDay;

	@Schema(description = "策略数量")
	private Integer traderNum;

	@Schema(description = "跟单数量")
	private Integer followNum;

	@Schema(description = "总持仓订单数量")
	private Integer total;
	@Schema(description = "净值")
	private BigDecimal euqit;
	@Schema(description = "盈亏")
	private BigDecimal profit;
	@Schema(description = "总手数")
	private BigDecimal lots;

	@Schema(description = "复制状态，0：失败 1：进行中 2：成功")
	private Integer copyStatus;

	@Schema(description = "是否开启flink，0为停止，1为运行")
	private Integer isFlink;

	@Schema(description = "是否选择账号，0为否，1为是")
	private Integer isSelectAccount;

	@Schema(description = "vps用户")
	private List<String> userList;

}