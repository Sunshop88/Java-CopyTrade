package net.maku.followcom.vo;

import lombok.Data;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;
import com.fhs.core.trans.anno.Trans;
import com.fhs.core.trans.constant.TransType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账号持仓订单
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowOrderActiveExcelVO implements TransPojo {
	@ExcelProperty("主键")
	private Long id;

	@ExcelProperty("订单号")
	private Integer orderNo;

	@ExcelProperty("类型0-buy 1-sell 6-balance 7-credit")
	private Integer type;

	@ExcelProperty("开仓时间")
	private LocalDateTime openTime;

	@ExcelProperty("平仓时间")
	private LocalDateTime closeTime;

	@ExcelProperty("魔术号")
	private Integer magic;

	@ExcelProperty("手数")
	private BigDecimal size;

	@ExcelProperty("开仓价格")
	private BigDecimal openPrice;

	@ExcelProperty("当前/平仓价格")
	private BigDecimal closePrice;

	@ExcelProperty("止损价格")
	private BigDecimal sl;

	@ExcelProperty("止盈价格")
	private BigDecimal tp;

	@ExcelProperty("实时获利")
	private BigDecimal profit;

	@ExcelProperty("手续费")
	private BigDecimal commission;

	@ExcelProperty("税费")
	private BigDecimal taxes;

	@ExcelProperty("库存费")
	private BigDecimal swap;

	@ExcelProperty("交易品种")
	private String symbol;

	@ExcelProperty("注释")
	private String comment;

	@ExcelProperty("账号ID")
	private Long traderId;

	@ExcelProperty("保证金")
	private BigDecimal margin;

	@ExcelProperty("行情账户ID")
	private Long marketAccountId;

	@ExcelProperty("盈利点数")
	private Integer profitPoint;

}