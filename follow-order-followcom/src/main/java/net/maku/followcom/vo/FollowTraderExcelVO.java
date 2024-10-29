package net.maku.followcom.vo;

import lombok.Data;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;
import com.fhs.core.trans.anno.Trans;
import com.fhs.core.trans.constant.TransType;
import java.math.BigDecimal;

/**
 * mt4账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowTraderExcelVO implements TransPojo {
	private Long id;

	@ExcelProperty("mt4账号")
	private String account;

	@ExcelProperty("类型0-信号源 1-跟单者")
	private Integer type;

	@ExcelProperty("密码")
	private String password;

	@ExcelProperty("服务器id")
	private Integer serverId;

	@ExcelProperty("状态0-正常 1-异常")
	private Integer status;

	@ExcelProperty("异常信息")
	private String statusExtra;

	@ExcelProperty("服务器ip")
	private String ipAddr;

	@ExcelProperty("服务器名称")
	private String serverName;

	@ExcelProperty("跟单状态0-未开启 1-已开启")
	private Integer followStatus;

	@ExcelProperty("喊单账号id")
	private Long followTraderId;

	@ExcelProperty("下单模式0-固定手数 1-手数比例 2-净值比例")
	private Integer followMode;

	@ExcelProperty("下单模式参数")
	private BigDecimal followParam;

	@ExcelProperty("下单方式")
	private Integer placedType;

	@ExcelProperty("跟单开仓状态 0-未开启 1-开启")
	private Integer followOpen;

	@ExcelProperty("跟单平仓状态 0-未开启 1-开启")
	private Integer followClose;

	@ExcelProperty("跟单补单状态 0-未开启 1-开启")
	private Integer followRep;

	@ExcelProperty("跟单方向0-正向1-反向")
	private Integer followDirection;

	@ExcelProperty("备注")
	private String remark;

	@ExcelProperty("净值")
	private BigDecimal euqit;

	@ExcelProperty("余额")
	private BigDecimal balance;

	@ExcelProperty("可用预付款")
	private BigDecimal freeMargin;

	@ExcelProperty("预付款比例")
	private BigDecimal marginProportion;

	@ExcelProperty("杠杆")
	private Integer leverage;

	@ExcelProperty("订单量")
	private Integer orderNum;

	@ExcelProperty("持仓手数")
	private BigDecimal orderSize;

	@ExcelProperty("盈亏")
	private BigDecimal profitLoss;

	@ExcelProperty("倍数")
	private BigDecimal multiple;

	@ExcelProperty("sell数量")
	private Integer orderSell;

	@ExcelProperty("buy数量")
	private Integer orderBuy;

}