package net.maku.followcom.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 交易日志
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_trader_log")
public class FollowTraderLogEntity {
	@TableId(type = IdType.AUTO)
	@TableField(value = "id")
	private Long id;

	/**
	* 日志记录
	*/
	@TableField(value = "log_detail")
	private String logDetail;

	/**
	* vps名称
	*/
	@TableField(value = "vps_name")
	private String vpsName;

	/**
	* vpsId
	*/
	@TableField(value = "vps_id")
	private Integer vpsId;

	/**
	* 账号类型0-信号源 1-跟单者
	*/
	@TableField(value = "trader_type")
	private Integer traderType;

	/**
	* 类型0-新增 1-编辑 2-删除 3-下单 4-平仓 5-补单
	*/
	@TableField(value = "type")
	private Integer type;

	/**
	* 是否主动0-否 1-是
	*/
	@TableField(value = "if_initiative")
	private Integer ifInitiative;

	/**
	* 单号
	*/
	@TableField(value = "order_no")
	private Integer orderNo;

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