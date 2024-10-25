package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * vps信息
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "vps信息")
public class FollowVpsInfoVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "名称")
	private Integer total;

	@Schema(description = "对外开放数量")
	private Integer openNum;

	@Schema(description = "运行中数量")
	private Integer runningNum;

}