package net.maku.followcom.vo;

import lombok.Data;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;
import com.fhs.core.trans.anno.Trans;
import com.fhs.core.trans.constant.TransType;
import java.util.Date;

/**
 * 品种规格
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowSysmbolSpecificationExcelVO implements TransPojo {
	private Long id;

	@ExcelProperty("账号id")
	private Long traderId;

	@ExcelProperty("品种")
	private String symbol;

	@ExcelProperty("品种类型")
	private String profitMode;

	@ExcelProperty("最小手数")
	private Double minLot;

	@ExcelProperty("最大手数")
	private Double maxLot;

	@ExcelProperty("步长")
	private Double lotStep;

	@ExcelProperty("买入库存费")
	private Double swapLong;

	@ExcelProperty("卖出库存费")
	private Double swapShort;

	@ExcelProperty("合约大小")
	private Double contractSize;

	@ExcelProperty("小数位")
	private Integer digits;

	@ExcelProperty("预付款货币")
	private String marginCurrency;

}