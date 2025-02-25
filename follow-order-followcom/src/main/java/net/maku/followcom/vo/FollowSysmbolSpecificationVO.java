package net.maku.followcom.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import lombok.Data;
import java.io.Serializable;

/**
 * 品种规格
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "品种规格")
public class FollowSysmbolSpecificationVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;

	@Schema(description = "账号id")
	private Long traderId;

	@Schema(description = "品种")
	private String symbol;

	@Schema(description = "品种类型")
	private String profitMode;

	@Schema(description = "最小手数")
	private Double minLot;

	@Schema(description = "最大手数")
	private Double maxLot;

	@Schema(description = "步长")
	private Double lotStep;

	@Schema(description = "买入库存费")
	private Double swapLong;

	@Schema(description = "卖出库存费")
	private Double swapShort;

	@Schema(description = "合约大小")
	private Double contractSize;

	@Schema(description = "小数位")
	private Integer digits;

	@Schema(description = "预付款货币")
	private String marginCurrency;

	@Schema(description = "版本号")
	private Integer version;

	@Schema(description = "删除标识 0：正常 1：已删除")
	private Integer deleted;

	@Schema(description = "创建者")
	private Long creator;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "更新者")
	private Long updater;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	@Schema(description = "保证金计算模式")
	private String marginMode;

	@Schema(description = "交易货币")
	private String currency;

	@Schema(description = "止损/止盈水平")
	private Integer stopsLevel;

	@Schema(description = "点差")
	private Integer spread;

	@Schema(description = "冻结水平")
	private Integer freezeLevel;

	@Schema(description = "保证金除数")
	private Double marginDivider;

	@Schema(description = "标准品种")
	private String stdSymbol;


}