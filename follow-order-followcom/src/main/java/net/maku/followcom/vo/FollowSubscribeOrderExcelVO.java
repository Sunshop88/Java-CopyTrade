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
 * 订阅关系表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowSubscribeOrderExcelVO implements TransPojo {
	@ExcelProperty("主键")
	private Long id;

	@ExcelProperty("喊单者的ID")
	private Long masterId;

	@ExcelProperty("喊单者的订单号")
	private Integer masterTicket;

	@ExcelProperty("喊单者开仓类型buy sell等")
	private Integer masterType;

	@ExcelProperty("喊单者开仓时间(MT4时间)")
	private Date masterOpenTime;

	@ExcelProperty("侦测到开仓信号的时间")
	private Date detectedOpenTime;

	@ExcelProperty("喊单者平仓时间(MT4时间)")
	private Date masterCloseTime;

	@ExcelProperty("侦测到平仓信号的时间(系统时间)")
	private Date detectedCloseTime;

	@ExcelProperty("喊单者的开仓手数")
	private BigDecimal masterLots;

	@ExcelProperty("喊单者的注释")
	private String comment;

	@ExcelProperty("喊单者收益 swap commission profit的总和")
	private BigDecimal masterProfit;

	@ExcelProperty("喊单者开仓的原始货币对")
	private String masterSymbol;

	@ExcelProperty("跟单者的ID")
	private Long slaveId;

	@ExcelProperty("跟单者对应的订单号，允许为null，为null时就是跟单没跟上")
	private Integer slaveTicket;

	@ExcelProperty("跟单者开仓类型buy sell等")
	private Integer slaveType;

	@ExcelProperty("跟单者开仓时间(MT4时间)")
	private Date slaveOpenTime;

	@ExcelProperty("跟单者收到喊单者开仓信号的时间")
	private Date slaveReceiveTime;

	@ExcelProperty("跟单者平仓时间(MT4时间)")
	private Date slaveCloseTime;

	@ExcelProperty("跟单者平仓时间(MT4时间)")
	private Date slaveReceiveCloseTime;

	@ExcelProperty("跟单者的开仓手数")
	private Double slaveLots;

	@ExcelProperty("跟随者收益 swap commission profit的总和")
	private BigDecimal slaveProfit;

	@ExcelProperty("跟单者开仓价格")
	private Double slaveOpenPrice;

	@ExcelProperty("跟单者开仓的原始货币对")
	private String slaveSymbol;

	@ExcelProperty("跟单者注释")
	private String slaveComment;

	@ExcelProperty("下单模式0-固定手数 1-手数比例 2-净值比例")
	private Integer followMode;

	@ExcelProperty("下单模式参数")
	private BigDecimal followParam;

	@ExcelProperty("跟单者跟单方向")
	private String direction;

	@ExcelProperty("类型0-信号源 1-跟单者")
	private Integer masterOrSlave;

}