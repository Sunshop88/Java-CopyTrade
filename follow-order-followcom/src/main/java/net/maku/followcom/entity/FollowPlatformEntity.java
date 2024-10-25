package net.maku.followcom.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台管理
 *
 * @author 阿沐 babamu@126.com
 * @since 1.0.0 2024-09-11
 */

@Data
@TableName("follow_platform")
public class FollowPlatformEntity {
	/**
	 * ID
	 */
	@TableId(type = IdType.AUTO)
	@TableField(value = "id")
	private Long id;

	/**
	 * 券商名称
	 */
	@TableField(value = "broker_name")
	private String brokerName;

	/**
	 * 平台类型
	 */
	@TableField(value = "platform_type")
	private String platformType;

	/**
	 * 服务器
	 */
	@TableField(value = "server")
	private String server;

	/**
	 * 服务器节点
	 */
	@TableField(value = "server_node")
	private String serverNode;

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