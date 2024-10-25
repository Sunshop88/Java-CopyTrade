package net.maku.followcom.vo;

import lombok.Data;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;
import com.fhs.core.trans.anno.Trans;
import com.fhs.core.trans.constant.TransType;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 所有MT4账号的历史订单
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowOrderHistoryExcelVO implements TransPojo {
	@ExcelProperty("id")
	private String id;

	@ExcelProperty("订单")
	private Integer orderNo;

	@ExcelProperty("类型0-buy 1-sell 6-balance 7-credit")
	private Integer type;

	@ExcelProperty("开仓时间")
	private Date openTime;

	@ExcelProperty("平仓时间")
	private Date closeTime;

	@ExcelProperty("手数")
	private BigDecimal size;

	@ExcelProperty("交易品种")
	private String symbol;

	@ExcelProperty("开仓价格")
	private BigDecimal openPrice;

	@ExcelProperty("平仓价格")
	private BigDecimal closePrice;

	@ExcelProperty("止损")
	private BigDecimal sl;

	@ExcelProperty("止盈")
	private BigDecimal tp;

	@ExcelProperty("手续费")
	private BigDecimal commission;

	@ExcelProperty("税费")
	private BigDecimal taxes;

	@ExcelProperty("库存费")
	private BigDecimal swap;

	@ExcelProperty("获利")
	private BigDecimal profit;

	@ExcelProperty("注释")
	private String comment;

	@ExcelProperty("魔数")
	private Integer magic;

	@ExcelProperty("写库时间")
	private Integer realTime;

	@ExcelProperty("MT4账号id")
	private String traderId;

	@ExcelProperty("MT4账号")
	private String account;

	@ExcelProperty("盈利点数")
	private Integer profitPoint;

}