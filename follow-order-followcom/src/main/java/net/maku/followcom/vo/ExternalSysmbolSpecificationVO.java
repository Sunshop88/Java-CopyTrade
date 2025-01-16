package net.maku.followcom.vo;

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


	@JsonProperty(value = "Symbol")
	private String symbol;

	@JsonProperty(value = "LotMin")
	private Double lotMin;

	@JsonProperty(value = "LotMax")
	private Double lotMax;

	@JsonProperty(value = "LotStep")
	private Double lotStep;

	@JsonProperty(value = "SwapLong")
	private Double swapLong;

	@JsonProperty(value = "SwapShort")
	private Double swapShort;

	@JsonProperty(value = "ContractSize")
	private Double contractSize;

	@JsonProperty(value = "Digits")
	private Integer digits;

	@JsonProperty(value = "StopsLevel")
	private Integer stopsLevel;

	@JsonProperty(value = "MarginCurrency")
	private String marginCurrency;

	@JsonProperty(value = "SwapType")
	private Integer swapType;

}