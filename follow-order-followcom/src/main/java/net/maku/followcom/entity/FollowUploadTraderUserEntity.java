package net.maku.followcom.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;
import java.util.Date;

/**
 * 上传账号记录表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_upload_trader_user")
public class FollowUploadTraderUserEntity {
	/**
	* ID
	*/
	@TableId
	@TableField(value = "id")
	private Long id;

	/**
	* 文件上传时间
	*/
	@TableField(value = "upload_time")
	private Date uploadTime;

	/**
	* 操作人
	*/
	@TableField(value = "operator")
	private String operator;

	/**
	* 状态 0：处理中 1：处理完成
	*/
	@TableField(value = "status")
	private Integer status;

	/**
	* 上传数据数量
	*/
	@TableField(value = "upload_total")
	private Long uploadTotal;

	/**
	* 成功数量
	*/
	@TableField(value = "success_count")
	private Long successCount;

	/**
	* 失败数量
	*/
	@TableField(value = "failure_count")
	private Long failureCount;

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
	private Date createTime;

	/**
	* 更新者
	*/
	@TableField(value = "updater", fill = FieldFill.INSERT_UPDATE)
	private Long updater;

	/**
	* 更新时间
	*/
	@TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
	private Date updateTime;

}