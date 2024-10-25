package net.maku.followcom.vo;

import lombok.Data;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;
import com.fhs.core.trans.anno.Trans;
import com.fhs.core.trans.constant.TransType;
import java.util.Date;

/**
 * 平仓记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowOrderCloseExcelVO implements TransPojo {
	private Integer id;

	@ExcelProperty("品种类型")
	private String symbol;

	@ExcelProperty("账号")
	private String account;

	@ExcelProperty("账号id")
	private Integer traderId;

	@ExcelProperty("类型0-buy 1-sell")
	private Integer type;

	@ExcelProperty("总单数")
	private Integer totalNum;

	@ExcelProperty("成功单数")
	private Integer successNum;

	@ExcelProperty("失败单数")
	private Integer failNum;

	@ExcelProperty("间隔时间 秒")
	private Integer intervalTime;

	@ExcelProperty("状态0-进行中 1-已完成")
	private Integer status;

}