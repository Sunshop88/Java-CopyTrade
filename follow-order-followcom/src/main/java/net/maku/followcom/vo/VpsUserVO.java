package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户vps
 */
@Data
@Schema(description = "用户vps")
public class VpsUserVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "vps名称")
	private String name;

	@Schema(description = "vpsid")
	private Integer id;
}