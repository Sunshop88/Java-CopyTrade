package net.maku.followcom.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 下单子指令
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_order_instruct_sub")
public class FollowOrderInstructSubEntity {
	@TableId(type = IdType.AUTO)
	@TableField(value = "id")
	private Long id;

	/**
	* 总指令id
	*/
	@TableField(value = "instruct_id")
	private Integer instructId;

	/**
	* 账号
	*/
	@TableField(value = "account")
	private String account;

	/**
	* 账号类型 MT4或MT5
	*/
	@TableField(value = "account_type")
	private String accountType;

	/**
	* 品种
	*/
	@TableField(value = "symbol")
	private String symbol;

	/**
	* 服务器
	*/
	@TableField(value = "platform")
	private String platform;

	/**
	* 类型 0-buy 1-sell
	*/
	@TableField(value = "type")
	private Integer type;

	/**
	* 状态0-成功1-各类错误
	*/
	@TableField(value = "status")
	private Integer status;

	/**
	* 手数
	*/
	@TableField(value = "lots")
	private BigDecimal lots;

	/**
	* 指令开始时间
	*/
	@TableField(value = "start_time")
	private Date startTime;

	/**
	* 指令结束时间
	*/
	@TableField(value = "end_time")
	private Date endTime;

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