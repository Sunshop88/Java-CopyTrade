package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import lombok.Data;
import java.io.Serializable;
import net.maku.framework.common.utils.DateUtils;
import java.util.Date;

/**
 * 账号初始表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "账号初始表")
public class FollowTraderUserVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;

	@Schema(description = "账号")
	private String account;

	@Schema(description = "密码")
	private String password;

	@Schema(description = "平台id")
	private Integer platformId;

	@Schema(description = "平台服务器")
	private String platform;

	@Schema(description = "账号类型")
	private String accountType;

	@Schema(description = "服务器节点")
	private String serverNode;

	@Schema(description = "组别id")
	private Integer groupId;

	@Schema(description = "挂靠状态0-未挂靠 1-已挂靠")
	private Integer status;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "版本号")
	private Integer version;

	@Schema(description = "删除标识 0：正常 1：已删除")
	private Integer deleted;

	@Schema(description = "创建者")
	private Long creator;

	@Schema(description = "创建时间")
	private Date createTime;

	@Schema(description = "更新者")
	private Long updater;

	@Schema(description = "更新时间")
	private Date updateTime;

	@Schema(description = "上传文件id")
	private Integer uploadId;

	@Schema(description = "添加账号状态 0：成功 1：失败")
	private Integer upload_status;

}