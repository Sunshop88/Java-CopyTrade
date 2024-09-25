package net.maku.mascontrol.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

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

	@Schema(description = "总数量")
	private Integer totalNum;

	@Schema(description = "总下单成功数量")
	private Integer successNum;


	@Schema(description = "总下单失败数量")
	private Integer failNum;

	@Schema(description = "进度数量")
	private Integer scheduleNum;

	@Schema(description = "进度成功")
	private Integer scheduleSuccessNum;

	@Schema(description = "状态0-进行中 1-已完成")
	private Integer status;

}