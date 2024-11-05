package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 用户vps可查看列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "用户vps可查看列表")
public class FollowVpsUserVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;

	@Schema(description = "用户id")
	private Long userId;

	@Schema(description = "vpsId")
	private Integer vpsId;

	@Schema(description = "vps服务器名称")
	private String vpsName;

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