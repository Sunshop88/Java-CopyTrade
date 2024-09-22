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
 * 订单详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowOrderDetailExcelVO implements TransPojo {
	private Long id;

	@ExcelProperty("品种")
	private String symbol;

	@ExcelProperty("订单号")
	private Integer orderNo;

	@ExcelProperty("账号id")
	private Long traderId;

	@ExcelProperty("账号")
	private String account;

	@ExcelProperty("开仓请求时间")
	private Date requestTime;

	@ExcelProperty("开仓请求价格")
	private BigDecimal requestPrice;

	@ExcelProperty("开仓时间")
	private Date openTime;

	@ExcelProperty("开仓价格")
	private BigDecimal openPrice;

	@ExcelProperty("开仓价格滑点")
	private BigDecimal openPriceSlip;

	@ExcelProperty("手数")
	private BigDecimal size;

	@ExcelProperty("止盈")
	private BigDecimal tp;

	@ExcelProperty("止损")
	private BigDecimal sl;

	@ExcelProperty("手续费")
	private BigDecimal commission;

	@ExcelProperty("利息")
	private BigDecimal swap;

}