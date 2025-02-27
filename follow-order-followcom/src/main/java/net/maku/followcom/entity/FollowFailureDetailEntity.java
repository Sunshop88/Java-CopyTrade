package net.maku.followcom.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;

/**
 * 失败详情表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_failure_detail")
public class FollowFailureDetailEntity {
	/**
	* ID
	*/
	@TableId
	@TableField(value = "id")
	private Long id;

	/**
	* 账号类型 需为MT4或MT5
	*/
	@TableField(value = "platform_type")
	private String platformType;

	/**
	* 服务器
	*/
	@TableField(value = "server")
	private String server;

	/**
	* 节点
	*/
	@TableField(value = "node")
	private String node;

	/**
	* 账号
	*/
	@TableField(value = "account")
	private String account;

	/**
	* 是否修改MT4密码
	*/
	@TableField(value = "is_password")
	private Integer isPassword;

	/**
	* 记录id
	*/
	@TableField(value = "record_id")
	private Long recordId;

	/**
	* 类型 0：新增账号 1：修改密码 2：挂靠VPS
	*/
	@TableField(value = "type")
	private Integer type;

	/**
	* 失败原因
	*/
	@TableField(value = "remark")
	private String remark;

}