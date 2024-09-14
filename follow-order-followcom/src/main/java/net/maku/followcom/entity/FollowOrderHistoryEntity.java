package net.maku.followcom.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 所有MT4账号的历史订单
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_order_history")
public class FollowOrderHistoryEntity {
	/**
	* id
	*/
	@TableId
	@TableField(value = "id")
	private String id;

	/**
	* 订单
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
	private Date openTime;

	/**
	* 平仓时间
	*/
	@TableField(value = "close_time")
	private Date closeTime;

	/**
	* 手数
	*/
	@TableField(value = "size")
	private BigDecimal size;

	/**
	* 交易品种
	*/
	@TableField(value = "symbol")
	private String symbol;

	/**
	* 开仓价格
	*/
	@TableField(value = "open_price")
	private BigDecimal openPrice;

	/**
	* 平仓价格
	*/
	@TableField(value = "close_price")
	private BigDecimal closePrice;

	/**
	* 止损
	*/
	@TableField(value = "sl")
	private BigDecimal sl;

	/**
	* 止盈
	*/
	@TableField(value = "tp")
	private BigDecimal tp;

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
	* 获利
	*/
	@TableField(value = "profit")
	private BigDecimal profit;

	/**
	* 注释
	*/
	@TableField(value = "comment")
	private String comment;

	/**
	* 魔数
	*/
	@TableField(value = "magic")
	private Integer magic;

	/**
	* 写库时间
	*/
	@TableField(value = "real_time")
	private Integer realTime;

	/**
	* MT4账号id
	*/
	@TableField(value = "trader_id")
	private String traderId;

	/**
	* MT4账号
	*/
	@TableField(value = "account")
	private String account;

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