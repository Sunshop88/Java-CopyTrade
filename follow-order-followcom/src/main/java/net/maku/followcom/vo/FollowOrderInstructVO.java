package net.maku.followcom.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import java.io.Serializable;
import net.maku.framework.common.utils.DateUtils;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 下单总指令表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "下单总指令表")
public class FollowOrderInstructVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Integer id;

	@Schema(description = "指令类型0-分配 1-复制")
	private Integer instructionType;

	@Schema(description = "品种")
	private String symbol;

	@Schema(description = "类型 0-buy 1-sell")
	private Integer type;

	@Schema(description = "手数范围开始")
	private BigDecimal minLotSize;

	@Schema(description = " 手数范围结束")
	private BigDecimal maxLotSize;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "下单总手数")
	private BigDecimal totalLots;

	@Schema(description = "下单总订单")
	private Integer totalOrders;

	@Schema(description = "间隔时间")
	private Integer intervalTime;

	@Schema(description = "成交手数")
	private BigDecimal tradedLots;

	@Schema(description = "成交订单")
	private Integer tradedOrders;

	@Schema(description = "状态0-执行中 1-全部成功 2-存在失败")
	private Integer status;

	@Schema(description = "版本号")
	private Integer version;

	@Schema(description = "删除标识  0：正常   1：已删除")
	private Integer deleted;

	@Schema(description = "创建者")
	private Long creator;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "更新者")
	private Long updater;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	@Schema(description = "指令结束时间")
	private LocalDateTime endTime;

	@Schema(description = "订单号")
	private String orderNo;

	@Schema(description = "失败订单")
	private Integer failOrder;

	@Schema(description = "操作人")
	private String creatorName;

}