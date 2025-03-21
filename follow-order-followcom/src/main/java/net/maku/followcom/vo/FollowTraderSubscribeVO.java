package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import java.io.Serializable;

/**
 * 订阅关系表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "订阅关系表")
public class FollowTraderSubscribeVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;

	@Schema(description = "交易员ID")
	private Long masterId;

	@Schema(description = "跟单者ID")
	private Long slaveId;

	@Schema(description = "跟随模式0-固定手数 1-手数比例 2-净值比例")
	private Integer followMode;

	@Schema(description = "跟单比例")
	private BigDecimal followParam;

	@Schema(description = "跟单状态0-未开启 1-已开启")
	private Integer followStatus;

	@Schema(description = "跟单开仓状态 0-未开启 1-开启")
	private Integer followOpen;

	@Schema(description = "跟单平仓状态 0-未开启 1-开启")
	private Integer followClose;

	@Schema(description = "跟单补单状态 0-未开启 1-开启")
	private Integer followRep;

	@Schema(description = "下单方式")
	private Integer placedType;

	@Schema(description = "止盈止损0-不跟随 1-跟随")
	private Integer tpSl;

	@Schema(description = "跟单方向0-正向1-反向")
	private Integer followDirection;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "订单量")
	private Integer orderNum;

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

	@Schema(description = "固定注释")
	private String fixedComment;

	@Schema(description = "注释类型0-英文 1-数字 2-英文+数字+符号")
	private Integer commentType;

	@Schema(description = "位数")
	private Integer digits;
}