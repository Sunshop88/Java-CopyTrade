package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import lombok.Data;
import java.io.Serializable;
import net.maku.framework.common.utils.DateUtils;
import java.math.BigDecimal;
import java.util.Date;

/**
 * mt4账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "mt4账号")
public class FollowTraderVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;

	@Schema(description = "mt4账号")
	private String account;

	@Schema(description = "类型0-信号源 1-跟单者")
	private Integer type;

	@Schema(description = "密码")
	private String password;

	@Schema(description = "服务器id")
	private Integer serverId;

	@Schema(description = "状态0-正常 1-异常")
	private Integer status;

	@Schema(description = "异常信息")
	private String statusExtra;

	@Schema(description = "服务器ip")
	private String ipAddr;

	@Schema(description = "服务器名称")
	private String serverName;

	@Schema(description = "跟单状态0-未开启 1-已开启")
	private Integer followStatus;

	@Schema(description = "喊单账号id")
	private Long followTraderId;

	@Schema(description = "下单模式0-固定手数 1-手数比例 2-净值比例")
	private Integer followMode;

	@Schema(description = "下单模式参数")
	private BigDecimal followParam;

	@Schema(description = "下单类型0-全部 1-多单 2-空单")
	private Integer followType;

	@Schema(description = "跟单开仓状态 0-未开启 1-开启")
	private Integer followOpen;

	@Schema(description = "跟单平仓状态 0-未开启 1-开启")
	private Integer followClose;

	@Schema(description = "跟单补单状态 0-未开启 1-开启")
	private Integer followRep;

	@Schema(description = "跟单方向0-正向1-反向")
	private Integer followDirection;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "净值")
	private BigDecimal euqit;

	@Schema(description = "余额")
	private BigDecimal balance;

	@Schema(description = "可用预付款")
	private BigDecimal freeMargin;

	@Schema(description = "预付款比例")
	private BigDecimal marginProportion;

	@Schema(description = "杠杆")
	private Integer leverage;

	@Schema(description = "订单量")
	private Integer orderNum;

	@Schema(description = "持仓手数")
	private BigDecimal orderSize;

	@Schema(description = "盈亏")
	private BigDecimal profitLoss;

	@Schema(description = "倍数")
	private BigDecimal multiple;

	@Schema(description = "sell数量")
	private Integer orderSell;

	@Schema(description = "buy数量")
	private Integer orderBuy;

	@Schema(description = "版本号")
	private Integer version;

	@Schema(description = "删除标识  0：正常   1：已删除")
	private Integer deleted;

	@Schema(description = "创建者")
	private Long creator;

	@Schema(description = "创建时间")
	private Date createTime;

	@Schema(description = "更新者")
	private Long updater;

	@Schema(description = "更新时间")
	private Date updateTime;

}