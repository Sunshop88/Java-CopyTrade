package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 报价数据
 */
@Data
@Schema(description = "报价数据")
public class MasServicesQuoteVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "卖价")
	private double bid;

	@Schema(description = "买价")
	private double ask;

}