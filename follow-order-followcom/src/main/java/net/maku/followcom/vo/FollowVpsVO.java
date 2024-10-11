package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

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
	private String name;

	@Schema(description = "ip地址")
	private String ipAddress;

	@Schema(description = "到期时间")
	private LocalDateTime expiryDate;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "是否对外开放，0为否，1为是")
	private Integer isOpen;

	@Schema(description = "是否状态，0为停止，1为运行")
	private Integer isActive;

	@Schema(description = "连接状态，0为异常，1为正常")
	private Integer connectionStatus;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "剩余到期天数")
	private Integer remainingDay;

}