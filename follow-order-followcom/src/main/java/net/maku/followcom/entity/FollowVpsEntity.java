package net.maku.followcom.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * vps列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_vps")
public class FollowVpsEntity {
	@TableId(type = IdType.AUTO)
	private Integer id;

	/**
	* 机器码
	*/
	@TableField(value = "client_id")
	private String clientId;

	/**
	* 名称
	*/
	@TableField(value = "name")
	private String name;

	/**
	* ip地址
	*/
	@TableField(value = "ip_address")
	private String ipAddress;

	/**
	* 到期时间
	*/
	@TableField(value = "expiry_date")
	private LocalDateTime expiryDate;

	/**
	* 备注
	*/
	@TableField(value = "remark")
	private String remark;

	/**
	* 是否对外开放，0为否，1为是
	*/
	@TableField(value = "is_open")
	private Integer isOpen;

	/**
	* 是否状态，0为停止，1为运行
	*/
	@TableField(value = "is_active")
	private Integer isActive;

	/**
	* 连接状态，0为异常，1为正常
	*/
	@TableField(value = "connection_status")
	private Integer connectionStatus;

	/**
	 * 复制状态，0：失败 1：进行中 2：成功
	 */
	@TableField(value = "copy_status")
	private Integer copyStatus;

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
	private LocalDateTime updateTime;

	/**
	 * 是否同步处理，0否，1是
	 */
	@TableField(value = "is_syn")
	private Integer isSyn;

	/**
	 * 是否开启flink，0为停止，1为运行
	 */
	@TableField(value = "is_flink")
	private Integer isFlink;

	/**
	 * 是否允许选择账号，0为关闭，1为开启
	 */
	@TableField(value = "is_select_account")
	private Integer isSelectAccount;

}