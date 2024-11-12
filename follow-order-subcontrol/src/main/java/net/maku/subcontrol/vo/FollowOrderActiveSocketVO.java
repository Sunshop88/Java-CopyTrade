package net.maku.subcontrol.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.followcom.vo.OrderRepairInfoVO;

import java.io.Serializable;
import java.util.List;

/**
 * 持仓及补单推送信息
 */
@Data
@Schema(description = "持仓及补单推送信息")
public class FollowOrderActiveSocketVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "持仓订单集合")
	private List<OrderActiveInfoVO> orderActiveInfoList;

	@Schema(description = "补单集合")
	private List<OrderRepairInfoVO> orderRepairInfoVOList;

}