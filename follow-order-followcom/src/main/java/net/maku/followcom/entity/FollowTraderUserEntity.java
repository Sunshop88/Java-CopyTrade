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
	* 账号类型 MT4或MT5
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
	* 排序 默认：1
	*/
	@TableField(value = "sort")
	private Integer sort;

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


}