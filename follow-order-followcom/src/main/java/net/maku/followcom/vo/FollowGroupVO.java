package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import net.maku.framework.common.utils.DateUtils;
import java.util.Date;

/**
 * 组别
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "组别")
public class FollowGroupVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "ID")
	private Integer id;

	@Schema(description = "组别名称")
	@NotBlank(message = "组别名称不能为空")
	@Size(max = 16, message = "组别名称长度不能超过16")
	private String name;

	@Schema(description = "账号数量")
	private Integer number;

	@Schema(description = "颜色")
	@NotBlank(message = "颜色不能为空")
	private String color;

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

}