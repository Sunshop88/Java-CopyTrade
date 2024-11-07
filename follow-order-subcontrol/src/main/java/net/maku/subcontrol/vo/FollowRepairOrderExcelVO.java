package net.maku.subcontrol.vo;

import lombok.Data;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;
import com.fhs.core.trans.anno.Trans;
import com.fhs.core.trans.constant.TransType;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 补单记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowRepairOrderExcelVO implements TransPojo {
	private Long id;

	@ExcelProperty("类型0-下单 1-平仓")
	private Integer type;

	@ExcelProperty("喊单者的ID")
	private Integer masterId;

	@ExcelProperty("喊单者账号")
	private Integer masterAccount;

	@ExcelProperty("喊单者的订单号")
	private Integer masterTicket;

	@ExcelProperty("喊单者开仓时间")
	private Date masterOpenTime;

	@ExcelProperty("喊单者开仓的原始货币对")
	private String masterSymbol;

	@ExcelProperty("喊单者的开仓手数")
	private BigDecimal masterLots;

	@ExcelProperty("跟单者的ID")
	private Integer slaveId;

	@ExcelProperty("跟单者的账号")
	private Integer slaveAccount;

	@ExcelProperty("订阅ID")
	private Integer subscribeId;

	@ExcelProperty("状态0-未处理 1-已处理")
	private Integer status;

}