package net.maku.mascontrol.vo;

import lombok.Data;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;
import com.fhs.core.trans.anno.Trans;
import com.fhs.core.trans.constant.TransType;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 下单总指令表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowOrderInstructExcelVO implements TransPojo {
	private Integer id;

	@ExcelProperty("指令类型0-分配 1-复制")
	private Integer instructionType;

	@ExcelProperty("品种")
	private String symbol;

	@ExcelProperty("类型 0-buy 1-sell")
	private Integer type;

	@ExcelProperty("手数范围开始")
	private BigDecimal minLotSize;

	@ExcelProperty(" 手数范围结束")
	private BigDecimal maxLotSize;

	@ExcelProperty("备注")
	private String remark;

	@ExcelProperty("下单总手数")
	private BigDecimal totalLots;

	@ExcelProperty("下单总订单")
	private Integer totalOrders;

	@ExcelProperty("成交手数")
	private BigDecimal tradedLots;

	@ExcelProperty("成交订单")
	private Integer tradedOrders;

	@ExcelProperty("状态0-执行中 1-全部成功 2-存在失败")
	private Integer status;

}