package net.maku.subcontrol.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 补单记录
 */
@Data
@Schema(description = "补单记录")
public class RepairSendVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "类型0-下单 1-平仓 2-一键补全")
	private Integer type;

	@Schema(description = "喊单者的ID")
	private Long masterId;

	@Schema(description = "跟单者的ID")
	private Long slaveId;

	@Schema(description = "订单号")
	private Integer orderNo;
	@Schema(description = "vpsId")
	private Integer vpsId;

}