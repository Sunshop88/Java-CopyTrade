package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.checkerframework.checker.units.qual.N;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 测速配置
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "测速配置")
public class FollowSpeedSettingVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;

	@Schema(description = "默认节点为最快节点 0：开启 1：关闭")
	@NotNull(message = "默认节点为最快节点不能为空")
	private Integer defaultServerNode;

	@Schema(description = "默认节点登录 0：开启 1：关闭")
	@NotNull(message = "默认节点登录不能为空")
	private Integer defaultServerNodeLogin;

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

}