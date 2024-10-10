package net.maku.followcom.vo;

import com.fhs.core.trans.vo.TransPojo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 数据概览
 */
@Data
@Schema(description = "数据概览")
public class TraderOverviewVO implements TransPojo {

	@Schema(description = "账号总数量")
	private Integer traderTotal;

	@Schema(description = "持仓总订单")
	private Integer orderTotal;

	@Schema(description = "做空订单数量")
	private Integer sellNum;

	@Schema(description = "做多订单数量")
	private Integer buyNum;

}