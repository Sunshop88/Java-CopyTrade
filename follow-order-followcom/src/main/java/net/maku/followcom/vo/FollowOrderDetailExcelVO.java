package net.maku.followcom.vo;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

	@ExcelIgnore
	private Integer type;

	@ExcelProperty("类型")
	private String typeName;

	@ExcelProperty("订单号")
	private Integer orderNo;

	@ExcelIgnore
	@ExcelProperty("账号id")
	private Long traderId;

	@ExcelProperty("账号")
	private String account;

	@ExcelProperty("开仓请求时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
	private LocalDateTime requestOpenTime;

	@ExcelProperty("开仓请求价格")
	private BigDecimal requestOpenPrice;

	@ExcelProperty("开仓时间")
	private Date openTime;

	@ExcelProperty("开仓价格")
	private BigDecimal openPrice;

	@ExcelProperty("开仓价格滑点")
	private BigDecimal openPriceSlip;

	@ExcelProperty("平仓请求时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
	private LocalDateTime requestCloseTime;

	@ExcelProperty("平仓请求价格")
	private BigDecimal requestClosePrice;

	@ExcelProperty("平仓时间")
	private LocalDateTime closeTime;

	@ExcelProperty("平仓价格")
	private BigDecimal closePrice;

	@ExcelProperty(value = "平仓价格滑点")
	private BigDecimal closePriceSlip;

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

	@ExcelIgnore
	@ExcelProperty("下单号")
	private String sendNo;

	@ExcelIgnore
	@ExcelProperty("异常信息")
	private String remark;

	@ExcelProperty("开仓时间差 毫秒")
	private Integer openTimeDifference;

	@ExcelProperty("平仓时间差 毫秒")
	private Integer closeTimeDifference;

	@ExcelProperty("开仓响应时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
	private LocalDateTime responseOpenTime;

	@ExcelProperty("平仓响应时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
	private LocalDateTime responseCloseTime;

	@ExcelProperty("盈亏")
	private BigDecimal profit;

	@ExcelProperty("券商")
	private String brokeName;

	@ExcelProperty("服务器")
	private String platform;

	@ExcelProperty("vps地址")
	private String ipAddr;

	@ExcelProperty("vps名称")
	private String serverName;


}