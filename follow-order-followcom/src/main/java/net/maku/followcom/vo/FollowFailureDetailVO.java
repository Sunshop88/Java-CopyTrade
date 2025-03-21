package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import java.io.Serializable;
import net.maku.framework.common.utils.DateUtils;

/**
 * 失败详情表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "失败详情表")
public class FollowFailureDetailVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "ID")
	private Long id;

	@Schema(description = "账号类型 需为MT4或MT5")
	private String platformType;

	@Schema(description = "服务器")
	private String server;

	@Schema(description = "节点")
	private String node;

	@Schema(description = "账号")
	private String account;

	@Schema(description = "是否修改MT4密码")
	private Integer isPassword;

	@Schema(description = "记录id")
	private Integer recordId;

	@Schema(description = "类型 0：新增账号 1：修改密码 2：挂靠VPS")
	private Integer type;

	@Schema(description = "失败原因")
	private String remark;

	@Schema(description = "状态 0：成功 1：失败")
	private Integer status;

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