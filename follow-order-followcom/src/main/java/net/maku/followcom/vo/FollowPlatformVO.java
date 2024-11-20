package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import net.maku.framework.common.utils.DateUtils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

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
	@NotBlank(message = "券商名称不能为空")
	@Size(max = 100, message = "券商名称长度不能超过100个字符")
	private String brokerName;

	@Schema(description = "平台类型", example = "MT4或MT5")
	@NotBlank(message = "平台类型不能为空")
	@Size(max = 50, message = "平台类型长度不能超过50个字符")
	private String platformType;

	@Schema(description = "服务器")
	@NotBlank(message = "服务器不能为空")
	@Size(max = 100, message = "服务器长度不能超过100个字符")
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
	@JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
	private LocalDateTime createTime;

	@Schema(description = "更新者")
	private Long updater;

	@Schema(description = "更新时间")
	@JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
	private LocalDateTime updateTime;

	@Schema(description = "服务名称集合")
	@NotNull(message = "服务名称集合不能为空")
	private List<String> platformList;

	@Schema(description = "服务名称已有集合")
	private List<String> serverList;

}