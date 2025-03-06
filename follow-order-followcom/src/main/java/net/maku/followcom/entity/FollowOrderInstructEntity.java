package net.maku.followcom.entity;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 下单总指令表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_order_instruct")
@Builder
public class FollowOrderInstructEntity {
	@TableId(type = IdType.AUTO)
	@TableField(value = "id")
	private Integer id;

	/**
	* 指令类型0-分配 1-复制
	*/
	@TableField(value = "instruction_type")
	private Integer instructionType;

	/**
	* 品种
	*/
	@TableField(value = "symbol")
	private String symbol;

	/**
	* 类型 0-buy 1-sell
	*/
	@TableField(value = "type")
	private Integer type;

	/**
	* 手数范围开始
	*/
	@TableField(value = "min_lot_size")
	private BigDecimal minLotSize;

	/**
	*  手数范围结束
	*/
	@TableField(value = "max_lot_size")
	private BigDecimal maxLotSize;

	/**
	* 备注
	*/
	@TableField(value = "remark")
	private String remark;

	/**
	* 下单总手数
	*/
	@TableField(value = "total_lots")
	private BigDecimal totalLots;

	/**
	* 下单总订单
	*/
	@TableField(value = "total_orders")
	private Integer totalOrders;

	/**
	* 成交手数
	*/
	@TableField(value = "traded_lots")
	private BigDecimal tradedLots;

	/**
	* 成交订单
	*/
	@TableField(value = "traded_orders")
	private Integer tradedOrders;

	/**
	* 状态0-执行中 1-全部成功 2-存在失败
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


	/**
	 * 指令结束时间
	 */
	@TableField(value = "end_time")
	private LocalDateTime endTime;

	/**
	 * 订单号
	 */
	@TableField(value = "order_no")
	private String orderNo;
}