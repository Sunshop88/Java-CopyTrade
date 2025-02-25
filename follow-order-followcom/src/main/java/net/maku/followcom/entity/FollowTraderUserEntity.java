package net.maku.followcom.entity;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 账号初始表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_trader_user")
public class FollowTraderUserEntity {
	@TableId
	@TableField(value = "id")
	private Long id;

	/**
	* 账号
	*/
	@TableField(value = "account")
	private String account;

	/**
	* 密码
	*/
	@TableField(value = "password")
	private String password;

	/**
	* 连接状态 0：正常 1：密码错误 2：连接异常 3：未挂靠VPS
	*/
	@TableField(value = "connection_status")
	private Integer connectionStatus;

	/**
	* 平台id
	*/
	@TableField(value = "platform_id")
	private Integer platformId;

	/**
	* 平台服务器
	*/
	@TableField(value = "platform")
	private String platform;

	/**
	* 券商名称
	*/
	@TableField(value = "broker_name")
	private String brokerName;

	/**
	* 挂靠vps 如:IP-名称-挂靠类型
	*/
	@TableField(value = "upload_status_name")
	private String uploadStatusName;

	/**
	* 账号类型
	*/
	@TableField(value = "account_type")
	private String accountType;

	/**
	* 服务器节点
	*/
	@TableField(value = "server_node")
	private String serverNode;

	/**
	 * 组别名称
	 */
	@TableField(value = "group_name")
	private String groupName;

	/**
	* 组别id
	*/
	@TableField(value = "group_id")
	private Integer groupId;

	/**
	* 挂靠状态0-未挂靠 1-已挂靠
	*/
	@TableField(value = "status")
	private Integer status;

	/**
	* 备注
	*/
	@TableField(value = "remark")
	private String remark;

	/**
	* 版本号
	*/
	@TableField(value = "version", fill = FieldFill.INSERT)
	private Integer version;

	/**
	* 删除标识 0：正常 1：已删除
	*/
	@TableField(value = "deleted", fill = FieldFill.INSERT)
	private Integer deleted;

	/**
	* 创建者
	*/
	@TableField(value = "creator", fill = FieldFill.INSERT)
	private Long creator;

	/**
	* 创建时间
	*/
	@TableField(value = "create_time", fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	/**
	* 更新者
	*/
	@TableField(value = "updater", fill = FieldFill.INSERT_UPDATE)
	private Long updater;

	/**
	* 更新时间
	*/
	@TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

	/**
	* 上传文件id
	*/
	@TableField(value = "upload_id")
	private Integer uploadId;

	/**
	* 添加账号状态 0：成功 1：失败
	*/
	@TableField(value = "upload_status")
	private Integer uploadStatus;
}