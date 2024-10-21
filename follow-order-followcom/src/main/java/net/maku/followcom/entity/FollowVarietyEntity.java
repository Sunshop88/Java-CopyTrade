package net.maku.followcom.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 品种匹配
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_variety")
public class FollowVarietyEntity {
	/**
	* ID
	*/
	@TableId(type = IdType.AUTO)
	@TableField(value = "id")
	private Integer id;

	/**
	 * 标准合约
	 */
	@TableField(value = "std_contract")
	private Integer stdContract;

	/**
	* 品种名称
	*/
	@TableField(value = "std_symbol")
	private String stdSymbol;

	/**
	* 券商名称
	*/
	@TableField(value = "broker_name")
	private String brokerName;

	/**
	* 券商对应的品种名称
	*/
	@TableField(value = "broker_symbol")
	private String brokerSymbol;

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