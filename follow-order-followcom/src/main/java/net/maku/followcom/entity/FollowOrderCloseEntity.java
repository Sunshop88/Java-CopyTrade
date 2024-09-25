package net.maku.followcom.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 平仓记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_order_close")
public class FollowOrderCloseEntity {
	@TableId(type = IdType.AUTO)
	@TableField(value = "id")
	private Integer id;

	/**
	* 品种类型
	*/
	@TableField(value = "symbol")
	private String symbol;

	/**
	* 账号
	*/
	@TableField(value = "account")
	private String account;

	/**
	* 账号id
	*/
	@TableField(value = "trader_id")
	private Integer traderId;

	/**
	* 类型0-buy 1-sell
	*/
	@TableField(value = "type")
	private Integer type;

	/**
	* 总单数
	*/
	@TableField(value = "total_num")
	private Integer totalNum;

	/**
	* 成功单数
	*/
	@TableField(value = "success_num")
	private Integer successNum;

	/**
	* 失败单数
	*/
	@TableField(value = "fail_num")
	private Integer failNum;

	/**
	* 间隔时间 秒
	*/
	@TableField(value = "interval_time")
	private Integer intervalTime;

	/**
	* 状态0-进行中 1-已完成
	*/
	@TableField(value = "status")
	private Integer status;

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

}