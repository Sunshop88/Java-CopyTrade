package net.maku.followcom.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
	@TableId(type = IdType.AUTO)
	private Long id;

	/**
	* 品种
	*/
	@TableField(value = "symbol")
	private String symbol;

	/**
	 * 类型 0-bug 1-sell
	 */
	@TableField(value = "type")
	private Integer type;


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
	@TableField(value = "request_open_time")
	private LocalDateTime requestOpenTime;

	/**
	* 开仓请求价格
	*/
	@TableField(value = "request_open_price")
	private BigDecimal requestOpenPrice;

	/**
	* 开仓时间
	*/
	@TableField(value = "open_time")
	private LocalDateTime openTime;

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
	 * 平仓请求时间
	 */
	@TableField(value = "request_close_time")
	private LocalDateTime requestCloseTime;

	/**
	 * 平仓请求价格
	 */
	@TableField(value = "request_close_price")
	private BigDecimal requestClosePrice;

	/**
	 * 平仓时间
	 */
	@TableField(value = "close_time")
	private LocalDateTime closeTime;

	/**
	 * 平仓价格
	 */
	@TableField(value = "close_price")
	private BigDecimal closePrice;

	/**
	 * 平仓价格滑点
	 */
	@TableField(value = "close_price_slip")
	private BigDecimal closePriceSlip;

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
	 * 下单号
	 */
	@TableField(value = "send_no")
	private String sendNo;


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
	 * 异常信息
	 */
	@TableField(value = "remark")
	private String remark;

	/**
	 * 开仓时间差 毫秒
	 */
	@TableField(value = "open_time_difference")
	private Integer openTimeDifference;

	/**
	 * 平仓时间差 毫秒
	 */
	@TableField(value = "close_time_difference")
	private Integer closeTimeDifference;

	/**
	 * 开仓响应时间
	 */
	@TableField(value = "response_open_time")
	private LocalDateTime responseOpenTime;

	/**
	 * 平仓响应时间
	 */
	@TableField(value = "response_close_time")
	private LocalDateTime responseCloseTime;

	/**
	 * 盈亏
	 */
	@TableField(value = "profit")
	private BigDecimal profit;

	/**
	 * 下单方式
	 */
	@TableField(value = "placed_type")
	private Integer placedType;

	/**
	 * 券商
	 */
	@TableField(value = "broker_name")
	private String brokeName;

	/**
	 * 服务器
	 */
	@TableField(value = "server")
	private String platform;

	/**
	 * vps地址
	 */
	@TableField(value = "ip_addr")
	private String ipAddr;

	/**
	 * vps名称
	 */
	@TableField(value = "server_name")
	private String serverName;

	/**
	 * 平仓ID
	 */
	@TableField(value = "close_id")
	private Integer closeId;

	/**
	 * 平仓状态
	 */
	@TableField(value = "close_status")
	private Integer closeStatus;
    /**
	 * 结算汇率
	 * */
	@TableField(value = "rate_margin")
	private Double rateMargin;

	//喊单账号
	@TableField(value = "source_user")
	private  String sourceUser;

	//魔术号
	@TableField(value = "magical")
	private  Integer magical;

	/**
	 * 节点
	 */
	@TableField(value = "server_host")
	private  String serverHost;

	/**
	 * 平仓VPS
	 */
	@TableField(value = "close_server_name")
	private String closeServerName;

	/**
	 * 平仓VPS地址
	 */
	@TableField(value = "close_ip_addr")
	private  String closeIpAddr;

	/**
	 * 平仓节点
	 */
	@TableField(value = "close_server_host")
	private  String closeServerHost;

	/**
	 * 是否外部0-否 1-是
	 */
	@TableField(value = "is_external")
	private  Integer isExternal;
	/**
	 * mt4备注
	 */
	@TableField(value = "comment")
	private String comment;
}
