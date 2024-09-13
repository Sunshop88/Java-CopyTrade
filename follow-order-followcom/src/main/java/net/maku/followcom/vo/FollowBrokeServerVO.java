package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 导入服务器列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "导入服务器列表")
public class FollowBrokeServerVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Integer id;

	@Schema(description = "服务器名称")
	private String serverName;

	@Schema(description = "服务器节点ip")
	private String serverNode;

	@Schema(description = "服务器节点端口")
	private String serverPort;

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
	private LocalDateTime updateTim;

}