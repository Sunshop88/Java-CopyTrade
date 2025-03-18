package net.maku.followcom.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * mt4账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_trader")
public class FollowTraderEntity {
	@TableId(type = IdType.AUTO)
	private Long id;

	/**
	* mt4账号
	*/
	@TableField(value = "account")
	private String account;

	/**
	* 类型0-信号源 1-跟单者
	*/
	@TableField(value = "type")
	private Integer type;

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
	* 状态0-正常 1-异常
	*/
	@TableField(value = "status")
	private Integer status;

	/**
	* 异常信息
	*/
	@TableField(value = "status_extra")
	private String statusExtra;

	/**
	* 服务器ip
	*/
	@TableField(value = "ip_addr")
	private String ipAddr;

	/**
	 * 服务器名称
	 */
	@TableField(value = "server_Id")
	private Integer serverId;

	/**
	* 服务器名称
	*/
	@TableField(value = "server_name")
	private String serverName;

	/**
	* 备注
	*/
	@TableField(value = "remark")
	private String remark;

	/**
	* 净值
	*/
	@TableField(value = "euqit")
	private BigDecimal euqit;

	/**
	* 余额
	*/
	@TableField(value = "balance")
	private BigDecimal balance;

	/**
	* 可用预付款
	*/
	@TableField(value = "free_margin")
	private BigDecimal freeMargin;

	/**
	* 预付款比例
	*/
	@TableField(value = "margin_proportion")
	private BigDecimal marginProportion;

	/**
	* 杠杆
	*/
	@TableField(value = "leverage")
	private Integer leverage;

	/**
	* 倍数
	*/
	@TableField(value = "multiple")
	private BigDecimal multiple;

	/**
	 * 账号的MT4/MT5服务器和北京时间的差
	 */
	@TableField(value = "diff")
	private Integer diff;

	/**
	 * 是否demo
	 */
	@TableField(value = "is_demo")
	private Boolean isDemo;

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
	 * 模板ID
	 */
	@TableField(value = "template_id")
	private Integer templateId;


	/**
	 * 跟单状态
	 */
	@TableField(value = "follow_status")
	private Integer followStatus;

	/**
	 * 是否首次同步（1是0否）
	 */
	@TableField(value = "is_first_sync")
	private Integer isFirstSync;

	/**
	 *登录节点地址
	 */
	@TableField(value = "login_node")
	private String loginNode;

	@TableField(value = "forex", updateStrategy = FieldStrategy.IGNORED)
	private String forex;
	@TableField(value = "cfd", updateStrategy = FieldStrategy.IGNORED)
	private String cfd;
}