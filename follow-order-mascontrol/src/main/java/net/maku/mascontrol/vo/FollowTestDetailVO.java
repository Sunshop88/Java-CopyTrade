package net.maku.mascontrol.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import java.io.Serializable;
import net.maku.framework.common.utils.DateUtils;
import java.util.Date;
import java.util.Map;
import java.util.Set;

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
	private Integer serverId;

	@Schema(description = "服务器名称")
	private String serverName;

	@Schema(description = "平台类型MT4/MT5")
	private String platformType;

	@Schema(description = "测速id")
	private Integer testId;

	@Schema(description = "服务器节点")
	private String serverNode;

	@Schema(description = "速度ms")
	private Integer speed;

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

	@Schema(description = "vps id")
	private Integer vpsId;

	@Schema(description = "vps名称")
	private String vpsName;

//	private Map<String, List<Integer>> vpsSpeedMap;
//	private List<String> platformTypes;
//	private List<String> serverNodes;

}