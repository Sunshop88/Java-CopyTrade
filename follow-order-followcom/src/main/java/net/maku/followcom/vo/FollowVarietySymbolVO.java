package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 品种匹配
 */
@Data
@Schema(description = "品种匹配")
public class FollowVarietySymbolVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "品种名称")
	private String stdSymbol;

}