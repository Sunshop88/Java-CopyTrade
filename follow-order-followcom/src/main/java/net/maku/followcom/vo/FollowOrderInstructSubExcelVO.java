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
 * 下单子指令
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowOrderInstructSubExcelVO implements TransPojo {
	private Long id;

	@ExcelProperty("总指令id")
	private Integer instructId;

	@ExcelProperty("账号")
	private String account;

	@ExcelProperty("账号类型 MT4或MT5")
	private String accountType;

	@ExcelProperty("品种")
	private String symbol;

	@ExcelProperty("服务器")
	private String platform;

	@ExcelProperty("类型 0-buy 1-sell")
	private Integer type;

	@ExcelProperty("状态0-成功1-各类错误")
	private Integer status;

	@ExcelProperty("手数")
	private BigDecimal lots;

	@ExcelProperty("指令开始时间")
	private Date startTime;

	@ExcelProperty("指令结束时间")
	private Date endTime;

}