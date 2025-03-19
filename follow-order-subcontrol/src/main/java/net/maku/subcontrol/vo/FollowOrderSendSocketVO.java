package net.maku.subcontrol.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.followcom.vo.OrderRepairInfoVO;

import java.io.Serializable;
import java.util.List;

/**
 * 下单推送信息
 */
@Data
@Schema(description = "下单推送信息")
public class FollowOrderSendSocketVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "sell价格")
	private double sellPrice;

	@Schema(description = "buy价格")
	private double buyPrice;

	@Schema(description = "进度数量")
	private Integer scheduleNum;

	@Schema(description = "进度成功")
	private Integer scheduleSuccessNum;

	@Schema(description = "进度失败")
	private Integer scheduleFailNum;

	@Schema(description = "状态0-进行中 1-已完成")
	private Integer status;

}