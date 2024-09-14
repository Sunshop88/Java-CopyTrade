package net.maku.followcom.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 订阅关系表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_subscribe_order")
public class FollowSubscribeOrderEntity {
	/**
	* 主键
	*/
	@TableId
	@TableField(value = "id")
	private Long id;

	/**
	* 喊单者的ID
	*/
	@TableField(value = "master_id")
	private Long masterId;

	/**
	* 喊单者的订单号
	*/
	@TableField(value = "master_ticket")
	private Integer masterTicket;

	/**
	* 喊单者开仓类型buy sell等
	*/
	@TableField(value = "master_type")
	private Integer masterType;

	/**
	* 喊单者开仓时间(MT4时间)
	*/
	@TableField(value = "master_open_time")
	private Date masterOpenTime;

	/**
	* 侦测到开仓信号的时间
	*/
	@TableField(value = "detected_open_time")
	private Date detectedOpenTime;

	/**
	* 喊单者平仓时间(MT4时间)
	*/
	@TableField(value = "master_close_time")
	private Date masterCloseTime;

	/**
	* 侦测到平仓信号的时间(系统时间)
	*/
	@TableField(value = "detected_close_time")
	private Date detectedCloseTime;

	/**
	* 喊单者的开仓手数
	*/
	@TableField(value = "master_lots")
	private BigDecimal masterLots;

	/**
	* 喊单者的注释
	*/
	@TableField(value = "comment")
	private String comment;

	/**
	* 喊单者收益 swap commission profit的总和
	*/
	@TableField(value = "master_profit")
	private BigDecimal masterProfit;

	/**
	* 喊单者开仓的原始货币对
	*/
	@TableField(value = "master_symbol")
	private String masterSymbol;

	/**
	* 跟单者的ID
	*/
	@TableField(value = "slave_id")
	private Long slaveId;

	/**
	* 跟单者对应的订单号，允许为null，为null时就是跟单没跟上
	*/
	@TableField(value = "slave_ticket")
	private Integer slaveTicket;

	/**
	* 跟单者开仓类型buy sell等
	*/
	@TableField(value = "slave_type")
	private Integer slaveType;

	/**
	* 跟单者开仓时间(MT4时间)
	*/
	@TableField(value = "slave_open_time")
	private Date slaveOpenTime;

	/**
	* 跟单者收到喊单者开仓信号的时间
	*/
	@TableField(value = "slave_receive_time")
	private Date slaveReceiveTime;

	/**
	* 跟单者平仓时间(MT4时间)
	*/
	@TableField(value = "slave_close_time")
	private Date slaveCloseTime;

	/**
	* 跟单者平仓时间(MT4时间)
	*/
	@TableField(value = "slave_receive_close_time")
	private Date slaveReceiveCloseTime;

	/**
	* 跟单者的开仓手数
	*/
	@TableField(value = "slave_lots")
	private Double slaveLots;

	/**
	* 跟随者收益 swap commission profit的总和
	*/
	@TableField(value = "slave_profit")
	private BigDecimal slaveProfit;

	/**
	* 跟单者开仓价格
	*/
	@TableField(value = "slave_open_price")
	private Double slaveOpenPrice;

	/**
	* 跟单者开仓的原始货币对
	*/
	@TableField(value = "slave_symbol")
	private String slaveSymbol;

	/**
	* 跟单者注释
	*/
	@TableField(value = "slave_comment")
	private String slaveComment;

	/**
	* 下单模式0-固定手数 1-手数比例 2-净值比例
	*/
	@TableField(value = "follow_mode")
	private Integer followMode;

	/**
	* 下单模式参数
	*/
	@TableField(value = "follow_param")
	private BigDecimal followParam;

	/**
	* 跟单者跟单方向
	*/
	@TableField(value = "direction")
	private String direction;

	/**
	* 类型0-信号源 1-跟单者
	*/
	@TableField(value = "master_or_slave")
	private Integer masterOrSlave;

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