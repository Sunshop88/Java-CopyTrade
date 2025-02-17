package net.maku.followcom.vo;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 品种规格
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "品种规格")
public class ExternalSysmbolSpecificationVO implements Serializable {
	private static final long serialVersionUID = 1L;


	@JSONField(name = "Symbol")
	private String symbol;

	@JSONField(name = "LotMin")
	private Double lotMin;

	@JSONField(name = "LotMax")
	private Double lotMax;

	@JSONField(name = "LotStep")
	private Double lotStep;

	@JSONField(name = "SwapLong")
	private Double swapLong;

	@JSONField(name = "SwapShort")
	private Double swapShort;

	@JSONField(name = "ContractSize")
	private Double contractSize;

	@JSONField(name = "Digits")
	private Integer digits;

	@JSONField(name = "StopsLevel")
	private Integer stopsLevel;

	@JSONField(name = "MarginCurrency")
	private String marginCurrency;

	@JSONField(name = "SwapType")
	private Integer swapType;

}