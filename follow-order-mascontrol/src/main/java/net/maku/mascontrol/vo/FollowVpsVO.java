package net.maku.mascontrol.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

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

	@Schema(description = "机器码")
	private String clientId;

	@Schema(description = "名称")
	private String name;

	@Schema(description = "ip地址")
	private String ipAddress;

	@Schema(description = "到期时间")
	private Date expiryDate;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "是否对外开放，0为否，1为是")
	private Integer isOpen;

	@Schema(description = "是否状态，0为停止，1为运行")
	private Integer isActive;

	@Schema(description = "连接状态，0为异常，1为正常")
	private Integer connectionStatus;

	@Schema(description = "租户ID")
	private Long tenantId;

	@Schema(description = "版本号")
	private Integer version;

	@Schema(description = "删除标识  0：正常   1：已删除")
	private Integer deleted;

	@Schema(description = "创建者")
	private Long creator;

	@Schema(description = "创建时间")
	private Date createTime;

	@Schema(description = "更新者")
	private Long updater;

	@Schema(description = "更新时间")
	private Date updateTime;

}