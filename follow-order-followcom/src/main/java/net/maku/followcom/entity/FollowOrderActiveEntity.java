package net.maku.followcom.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import online.mtapi.mt4.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账号持仓订单
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_order_active")
public class FollowOrderActiveEntity {

	/**
	* 主键
	*/
	@TableId(type = IdType.AUTO)
	private Long id;

	/**
	* 订单号
	*/
	@TableField(value = "order_no")
	private Integer orderNo;

	/**
	* 类型0-buy 1-sell 6-balance 7-credit
	*/
	@TableField(value = "type")
	private Integer type;

	/**
	* 开仓时间
	*/
	@TableField(value = "open_time")
	private LocalDateTime openTime;

	/**
	* 平仓时间
	*/
	@TableField(value = "close_time")
	private LocalDateTime closeTime;

	/**
	* 魔术号
	*/
	@TableField(value = "magic")
	private Integer magic;

	/**
	* 手数
	*/
	@TableField(value = "size")
	private BigDecimal size;

	/**
	* 开仓价格
	*/
	@TableField(value = "open_price")
	private BigDecimal openPrice;

	/**
	* 当前/平仓价格
	*/
	@TableField(value = "close_price")
	private BigDecimal closePrice;

	/**
	* 止损价格
	*/
	@TableField(value = "sl")
	private BigDecimal sl;

	/**
	* 止盈价格
	*/
	@TableField(value = "tp")
	private BigDecimal tp;

	/**
	* 实时获利
	*/
	@TableField(value = "profit")
	private BigDecimal profit;

	/**
	* 手续费
	*/
	@TableField(value = "commission")
	private BigDecimal commission;

	/**
	* 税费
	*/
	@TableField(value = "taxes")
	private BigDecimal taxes;

	/**
	* 库存费
	*/
	@TableField(value = "swap")
	private BigDecimal swap;

	/**
	* 交易品种
	*/
	@TableField(value = "symbol")
	private String symbol;

	/**
	* 注释
	*/
	@TableField(value = "comment")
	private String comment;

	/**
	* 账号ID
	*/
	@TableField(value = "trader_id")
	private Long traderId;

	/**
	* 保证金
	*/
	@TableField(value = "margin")
	private BigDecimal margin;

	/**
	* 行情账户ID
	*/
	@TableField(value = "market_account_id")
	private Long marketAccountId;

	/**
	* 盈利点数
	*/
	@TableField(value = "profit_point")
	private Integer profitPoint;

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

	public FollowOrderActiveEntity(Order order, long id) {
		this.orderNo = order.Ticket;
		this.type = order.Type.getValue();
		this.openTime = order.OpenTime;
		this.closeTime = order.CloseTime;
		this.magic = order.MagicNumber;
		this.size = BigDecimal.valueOf(order.Lots);
		this.openPrice = BigDecimal.valueOf(order.OpenPrice);
		this.closePrice = BigDecimal.valueOf(order.ClosePrice);
		this.sl = BigDecimal.valueOf(order.StopLoss);
		this.tp = BigDecimal.valueOf(order.TakeProfit);
		this.profit = BigDecimal.valueOf(order.Profit);
		this.commission = BigDecimal.valueOf(order.Commission);
		this.taxes = BigDecimal.ZERO;
		this.swap = BigDecimal.valueOf(order.Swap);
		this.symbol = order.Symbol;
		this.comment = order.Comment;
		this.traderId = id;
	}

}