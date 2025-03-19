package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
* 外部vps
*/
@Data
@Schema(description = "外部vps")
public class ClientVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "ID")
	private Long id;
	@Schema(description = "名称")
	private String name;
	@Schema(description = "IP")
	private String ip;
}