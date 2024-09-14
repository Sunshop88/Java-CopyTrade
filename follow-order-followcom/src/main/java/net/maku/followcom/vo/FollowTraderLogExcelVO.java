package net.maku.followcom.vo;

import lombok.Data;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;
import com.fhs.core.trans.anno.Trans;
import com.fhs.core.trans.constant.TransType;
import java.util.Date;

/**
 * 交易日志
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowTraderLogExcelVO implements TransPojo {
	private Long id;

	@ExcelProperty("日志记录")
	private String logDetail;

	@ExcelProperty("vps名称")
	private String vpsName;

	@ExcelProperty("vpsId")
	private Integer vpsId;

	@ExcelProperty("账号类型0-信号源 1-跟单者")
	private Integer traderType;

	@ExcelProperty("类型0-新增 1-编辑 2-删除 3-下单 4-平仓 5-补单")
	private Integer type;

	@ExcelProperty("是否主动0-否 1-是")
	private Integer ifInitiative;

	@ExcelProperty("单号")
	private Integer orderNo;

}