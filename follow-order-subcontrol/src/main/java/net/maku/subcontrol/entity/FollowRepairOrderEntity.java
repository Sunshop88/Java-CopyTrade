package net.maku.subcontrol.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 补单记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_repair_order")
public class FollowRepairOrderEntity {
	@TableId(type = IdType.AUTO)
	@TableField(value = "id")
	private Long id;

	/**
	* 类型0-下单 1-平仓
	*/
	@TableField(value = "type")
	private Integer type;

	/**
	* 喊单者的ID
	*/
	@TableField(value = "master_id")
	private Long masterId;

	/**
	* 喊单者账号
	*/
	@TableField(value = "master_account")
	private String masterAccount;

	/**
	* 喊单者的订单号
	*/
	@TableField(value = "master_ticket")
	private Integer masterTicket;

	/**
	* 喊单者开仓时间
	*/
	@TableField(value = "master_open_time")
	private LocalDateTime masterOpenTime;

	/**
	* 喊单者开仓的原始货币对
	*/
	@TableField(value = "master_symbol")
	private String masterSymbol;

	/**
	* 喊单者的开仓手数
	*/
	@TableField(value = "master_lots")
	private BigDecimal masterLots;

	/**
	* 跟单者的ID
	*/
	@TableField(value = "slave_id")
	private Long slaveId;

	/**
	* 跟单者的账号
	*/
	@TableField(value = "slave_account")
	private String slaveAccount;

	/**
	* 订阅ID
	*/
	@TableField(value = "subscribe_id")
	private Long subscribeId;

	/**
	* 状态0-未处理 1-已处理
	*/
	@TableField(value = "status")
	private Integer status;

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

}