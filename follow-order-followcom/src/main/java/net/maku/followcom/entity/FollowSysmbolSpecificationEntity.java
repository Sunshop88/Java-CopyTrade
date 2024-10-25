package net.maku.followcom.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 品种规格
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("follow_sysmbol_specification")
public class FollowSysmbolSpecificationEntity {
	@TableId(type = IdType.AUTO)
	@TableField(value = "id")
	private Long id;

	/**
	* 账号id
	*/
	@TableField(value = "trader_id")
	private Long traderId;

	/**
	* 品种
	*/
	@TableField(value = "symbol")
	private String symbol;

	/**
	* 品种类型
	*/
	@TableField(value = "profit_mode")
	private String profitMode;

	/**
	* 最小手数
	*/
	@TableField(value = "min_lot")
	private Double minLot;

	/**
	* 最大手数
	*/
	@TableField(value = "max_lot")
	private Double maxLot;

	/**
	* 步长
	*/
	@TableField(value = "lot_step")
	private Double lotStep;

	/**
	* 买入库存费
	*/
	@TableField(value = "swap_long")
	private Double swapLong;

	/**
	* 卖出库存费
	*/
	@TableField(value = "swap_short")
	private Double swapShort;

	/**
	* 合约大小
	*/
	@TableField(value = "contract_size")
	private Double contractSize;

	/**
	* 小数位
	*/
	@TableField(value = "digits")
	private Integer digits;

	/**
	* 预付款货币
	*/
	@TableField(value = "margin_currency")
	private String marginCurrency;

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

	@TableField(value = "margin_mode")
	private String marginMode;

	@TableField(value = "currency")
	private String currency;

	@TableField(value = "stops_level")
	private Integer stopsLevel;

	@TableField(value = "spread")
	private Integer spread;

	@TableField(value = "freeze_level")
	private Integer freezeLevel;

	@TableField(value = "margin_divider")
	private Double marginDivider;

}