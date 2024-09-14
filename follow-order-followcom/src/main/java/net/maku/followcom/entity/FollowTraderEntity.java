package net.maku.followcom.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.util.Date;

/**
 * mt4账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_trader")
public class FollowTraderEntity {
	@TableId
	@TableField(value = "id")
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
	* 服务器id
	*/
	@TableField(value = "server_id")
	private Integer serverId;

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
	@TableField(value = "server_name")
	private String serverName;

	/**
	* 跟单状态0-未开启 1-已开启
	*/
	@TableField(value = "follow_status")
	private Integer followStatus;

	/**
	* 喊单账号id
	*/
	@TableField(value = "follow_trader_id")
	private Long followTraderId;

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
	* 下单类型0-全部 1-多单 2-空单
	*/
	@TableField(value = "follow_type")
	private Integer followType;

	/**
	* 跟单开仓状态 0-未开启 1-开启
	*/
	@TableField(value = "follow_open")
	private Integer followOpen;

	/**
	* 跟单平仓状态 0-未开启 1-开启
	*/
	@TableField(value = "follow_close")
	private Integer followClose;

	/**
	* 跟单补单状态 0-未开启 1-开启
	*/
	@TableField(value = "follow_rep")
	private Integer followRep;

	/**
	* 跟单方向0-正向1-反向
	*/
	@TableField(value = "follow_direction")
	private Integer followDirection;

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
	* 订单量
	*/
	@TableField(value = "order_num")
	private Integer orderNum;

	/**
	* 持仓手数
	*/
	@TableField(value = "order_size")
	private BigDecimal orderSize;

	/**
	* 盈亏
	*/
	@TableField(value = "profit_loss")
	private BigDecimal profitLoss;

	/**
	* 倍数
	*/
	@TableField(value = "multiple")
	private BigDecimal multiple;

	/**
	* sell数量
	*/
	@TableField(value = "order_sell")
	private Integer orderSell;

	/**
	* buy数量
	*/
	@TableField(value = "order_buy")
	private Integer orderBuy;

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