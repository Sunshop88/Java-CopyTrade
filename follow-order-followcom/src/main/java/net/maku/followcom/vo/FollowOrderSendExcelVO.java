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
 * 下单记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowOrderSendExcelVO implements TransPojo {
	private Long id;

	@ExcelProperty("品种类型")
	private String symbol;

	@ExcelProperty("账号id")
	private Long traderId;

	@ExcelProperty("类型0-buy 1-sell")
	private Integer type;

	@ExcelProperty("总单数")
	private Integer totalNum;

	@ExcelProperty("成功单数")
	private Integer successNum;

	@ExcelProperty("失败单数")
	private Integer failNum;

	@ExcelProperty("总手数")
	private BigDecimal totalSzie;

	@ExcelProperty("开始手数范围from")
	private BigDecimal startSize;

	@ExcelProperty("结束手数范围to")
	private BigDecimal endSize;

	@ExcelProperty("状态0-进行中 1-已完成")
	private Integer status;

	@ExcelProperty("间隔时间 秒")
	private Integer interval;

	@ExcelProperty("完成时间")
	private Date finishTime;

}