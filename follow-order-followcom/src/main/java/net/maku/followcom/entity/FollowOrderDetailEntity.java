package net.maku.followcom.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_order_detail")
public class FollowOrderDetailEntity {
	@TableId
	@TableField(value = "id")
	private Long id;

	/**
	* 品种
	*/
	@TableField(value = "symbol")
	private String symbol;

	/**
	* 订单号
	*/
	@TableField(value = "order_no")
	private Integer orderNo;

	/**
	* 账号id
	*/
	@TableField(value = "trader_id")
	private Long traderId;

	/**
	* 账号
	*/
	@TableField(value = "account")
	private String account;

	/**
	* 开仓请求时间
	*/
	@TableField(value = "request_time")
	private Date requestTime;

	/**
	* 开仓请求价格
	*/
	@TableField(value = "request_price")
	private BigDecimal requestPrice;

	/**
	* 开仓时间
	*/
	@TableField(value = "open_time")
	private Date openTime;

	/**
	* 开仓价格
	*/
	@TableField(value = "open_price")
	private BigDecimal openPrice;

	/**
	* 开仓价格滑点
	*/
	@TableField(value = "open_price_slip")
	private BigDecimal openPriceSlip;

	/**
	* 手数
	*/
	@TableField(value = "size")
	private BigDecimal size;

	/**
	* 止盈
	*/
	@TableField(value = "tp")
	private BigDecimal tp;

	/**
	* 止损
	*/
	@TableField(value = "sl")
	private BigDecimal sl;

	/**
	* 手续费
	*/
	@TableField(value = "commission")
	private BigDecimal commission;

	/**
	* 利息
	*/
	@TableField(value = "swap")
	private BigDecimal swap;

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