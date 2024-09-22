package net.maku.followcom.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import lombok.Data;
import java.io.Serializable;
import net.maku.framework.common.utils.DateUtils;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 下单记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "下单记录")
public class FollowOrderSendVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;

	@Schema(description = "品种类型")
	private String symbol;

	@Schema(description = "账号id")
	private Long traderId;

	@Schema(description = "账号")
	private String account;

	@Schema(description = "类型0-buy 1-sell")
	private Integer type;

	@Schema(description = "总单数")
	private Integer totalNum;

	@Schema(description = "成功单数")
	private Integer successNum;

	@Schema(description = "失败单数")
	private Integer failNum;

	@Schema(description = "总手数")
	private BigDecimal totalSzie;

	@Schema(description = "开始手数范围from")
	private BigDecimal startSize;

	@Schema(description = "结束手数范围to")
	private BigDecimal endSize;

	@Schema(description = "状态0-进行中 1-已完成")
	private Integer status;

	@Schema(description = "间隔时间 秒")
	private Integer interval;

}