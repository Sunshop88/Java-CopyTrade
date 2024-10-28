package net.maku.followcom.entity;

import com.cld.utils.date.ThreeStrategyDateUtil;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import net.maku.followcom.pojo.EaOrderInfo;
import online.mtapi.mt4.Order;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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
	@TableId(type = IdType.AUTO)
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
	private LocalDateTime masterOpenTime;

	/**
	* 侦测到开仓信号的时间
	*/
	@TableField(value = "detected_open_time")
	private LocalDateTime detectedOpenTime;

	/**
	* 喊单者平仓时间(MT4时间)
	*/
	@TableField(value = "master_close_time")
	private LocalDateTime masterCloseTime;

	/**
	* 侦测到平仓信号的时间(系统时间)
	*/
	@TableField(value = "detected_close_time")
	private LocalDateTime detectedCloseTime;

	/**
	 * 当前持仓
	 */
	@TableField(value = "slave_position")
	private BigDecimal slavePosition;

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
	private LocalDateTime slaveOpenTime;

	/**
	* 跟单者收到喊单者开仓信号的时间
	*/
	@TableField(value = "slave_receive_time")
	private LocalDateTime slaveReceiveTime;

	/**
	* 跟单者平仓时间(MT4时间)
	*/
	@TableField(value = "slave_close_time")
	private LocalDateTime slaveCloseTime;

	/**
	* 跟单者平仓时间(MT4时间)
	*/
	@TableField(value = "slave_receive_close_time")
	private LocalDateTime slaveReceiveCloseTime;

	/**
	* 跟单者的开仓手数
	*/
	@TableField(value = "slave_lots")
	private BigDecimal slaveLots;

	/**
	* 跟随者收益 swap commission profit的总和
	*/
	@TableField(value = "slave_profit")
	private BigDecimal slaveProfit;

	/**
	* 跟单者开仓价格
	*/
	@TableField(value = "slave_open_price")
	private BigDecimal slaveOpenPrice;

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
	private Integer direction;

	/**
	* 类型0-信号源 1-跟单者
	*/
	@TableField(value = "master_or_slave")
	private Integer masterOrSlave;

	/**
	 * 附加信息
	 */
	@TableField(value = "extra")
	private String extra;

	/**
	 * 标记信息
	 */
	@TableField(value = "flag")
	private Integer flag;

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


	public FollowSubscribeOrderEntity(EaOrderInfo orderInfo, FollowTraderEntity copier) {
		this.masterId = orderInfo.getMasterId();
		this.masterTicket = orderInfo.getTicket();
		this.masterSymbol = orderInfo.getOriSymbol();
		this.masterLots = BigDecimal.valueOf(orderInfo.getLots());
		this.masterType = orderInfo.getType();
		this.masterOpenTime = orderInfo.getOpenTime();
		this.comment = orderInfo.getComment();
		this.detectedOpenTime =orderInfo.getDetectedOpenTime();
		this.slaveId = copier.getId();
		this.slaveReceiveTime = orderInfo.getSlaveReceiveOpenTime();
	}

	public void setLeaderCopier(FollowTraderSubscribeEntity leaderCopier) {
		if (leaderCopier != null) {
			this.setFollowMode(leaderCopier.getFollowMode());
			this.setFollowParam(leaderCopier.getFollowParam());
			this.setDirection(leaderCopier.getFollowDirection());
		}
	}
	public void setCopierOrder(Order order, EaOrderInfo orderInfo) {
		Duration between = Duration.between(orderInfo.getOpenTime(), order.OpenTime.plus(1, ChronoUnit.DAYS));
//        long openDelay = between.toSeconds() % 3600;
//        this.setOpenDelay(openDelay);
//		this.setRatio(new BigDecimal(order.Lots / orderInfo.getLots()));
		this.setSlaveType(order.Type.getValue());
		this.setSlaveTicket(order.Ticket);
		this.setSlaveOpenTime(order.OpenTime);
		this.setSlaveOpenPrice(BigDecimal.valueOf(order.OpenPrice));
		this.setComment(orderInfo.getComment());
		this.setSlaveComment(order.Comment);
		this.setSlavePosition(BigDecimal.valueOf(order.Lots));
	}

}