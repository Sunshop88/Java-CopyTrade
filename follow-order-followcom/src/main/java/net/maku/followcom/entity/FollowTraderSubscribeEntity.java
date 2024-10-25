package net.maku.followcom.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订阅关系表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_trader_subscribe")
public class FollowTraderSubscribeEntity {
	@TableId(type = IdType.AUTO)
	@TableField(value = "id")
	private Long id;

	/**
	* 交易员ID
	*/
	@TableField(value = "master_id")
	private Long masterId;

	/**
	* 跟单者ID
	*/
	@TableField(value = "slave_id")
	private Long slaveId;

	/**
	* 跟随模式0-固定手数 1-手数比例 2-净值比例
	*/
	@TableField(value = "follow_mode")
	private Integer followMode;

	/**
	* 跟单手数
	*/
	@TableField(value = "follow_lots")
	private BigDecimal followLots;

	/**
	* 跟单比例
	*/
	@TableField(value = "follow_param")
	private BigDecimal followParam;

	/**
	* 跟单状态0-未开启 1-已开启
	*/
	@TableField(value = "follow_status")
	private Integer followStatus;

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
	* 下单类型0-全部 1-多单 2-空单
	*/
	@TableField(value = "follow_type")
	private Integer followType;

	/**
	* 止盈止损0-不跟随 1-跟随
	*/
	@TableField(value = "tp_sl")
	private Integer tpSl;

	/**
	* 跟单方向0-正向1-反向
	*/
	@TableField(value = "follow_direction")
	private Integer followDirection;

	/**
	* 暂停订阅0-否 1-是
	*/
	@TableField(value = "pause")
	private Integer pause;

	/**
	* 备注
	*/
	@TableField(value = "remark")
	private String remark;

	/**
	* 订单量
	*/
	@TableField(value = "order_num")
	private Integer orderNum;

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