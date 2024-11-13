package net.maku.mascontrol.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 测速详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_test_detail")
public class FollowTestDetailEntity {
	@TableId(type = IdType.AUTO)
	private Long id;

	/**
	* 服务器id
	*/
	@TableField(value = "server_id")
	private Integer serverId;

	/**
	* 服务器名称
	*/
	@TableField(value = "server_name")
	private String serverName;

	/**
	* 平台类型MT4/MT5
	*/
	@TableField(value = "platform_type")
	private String platformType;

	/**
	* 测速id
	*/
	@TableField(value = "test_id")
	private Integer testId;

	/**
	* 服务器节点
	*/
	@TableField(value = "sever_node")
	private String severNode;

	/**
	* 速度ms
	*/
	@TableField(value = "speed")
	private Integer speed;

	/**
	* 版本号
	*/
	@TableField(value = "version", fill = FieldFill.INSERT)
	private Integer version;

	/**
	* 删除标识  0：正常   1：已删除
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
	private LocalDateTime updateTim;

}