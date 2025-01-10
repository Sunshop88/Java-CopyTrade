package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import net.maku.framework.common.utils.DateUtils;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 测速详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "测速详情")
public class FollowTestDetailVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;

	@Schema(description = "服务器id")
	@NotNull(message = "服务器ID不能为空")
	@Min(value = 1, message = "服务器ID必须大于等于1")
	private Integer serverId;

	@Schema(description = "服务器名称")
	@NotBlank(message = "服务器名称不能为空")
	@Size(max = 100, message = "服务器名称长度不能超过100个字符")
	private String serverName;

	@Schema(description = "平台类型MT4/MT5")
	@NotBlank(message = "平台类型不能为空")
	private String platformType;

	@Schema(description = "测速id")
	@NotNull(message = "测速ID不能为空")
	@Min(value = 1, message = "测速ID必须大于等于1")
	private Integer testId;

	@Schema(description = "服务器节点")
	@NotBlank(message = "服务器节点不能为空")
	private String serverNode;

	@Schema(description = "速度ms")
	@Min(value = 0, message = "速度必须大于等于0")
	private Integer speed;

	@Schema(description = "版本号")
	private Integer version;

	@Schema(description = "删除标识  0：正常   1：已删除")
	private Integer deleted;

	@Schema(description = "创建者")
	private Long creator;

	@Schema(description = "创建时间")
	@JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
	private LocalDateTime createTime;

	@Schema(description = "更新者")
	private Long updater;

	@Schema(description = "更新时间")
//	@JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
	private LocalDateTime updateTime;

	@Schema(description = "vps id")
	@NotNull(message = "VPS ID不能为空")
	@Min(value = 1, message = "VPS ID必须大于等于1")
	private Integer vpsId;

	@Schema(description = "vps名称")
	@NotBlank(message = "VPS名称不能为空")
	@Size(max = 100, message = "VPS名称长度不能超过100个字符")
	private String vpsName;

	@Schema(description = "服务器更新时间")
	private LocalDateTime serverUpdateTime;

	@Schema(description = "默认节点 0：是")
	private Integer isDefaultServer;

	@Schema(description = "broker_name")
	private String brokerName;

}