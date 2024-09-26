package net.maku.mascontrol.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;

import java.time.LocalDateTime;

/**
* 平台管理
*
* @author 阿沐 babamu@126.com
* @since 1.0.0 2024-09-11
*/
@Data
@Schema(description = "平台管理")
public class FollowPlatformVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "ID")
	private Long id;

	@Schema(description = "券商名称")
	private String brokerName;

	@Schema(description = "平台类型")
	private String platformType;

	@Schema(description = "服务器")
	private String server;

	@Schema(description = "服务器节点")
	private String serverNode;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "版本号")
	private Integer version;

	@Schema(description = "删除标识 0：正常 1：已删除")
	private Integer deleted;

	@Schema(description = "创建者")
	private String creator;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "更新者")
	private Long updater;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

}