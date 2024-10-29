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
public class FollowTraderSubscribeExcelVO implements TransPojo {
	private Long id;

	@ExcelProperty("交易员ID")
	private Long masterId;

	@ExcelProperty("跟单者ID")
	private Long slaveId;

	@ExcelProperty("跟随模式0-固定手数 1-手数比例 2-净值比例")
	private Integer followMode;

	@ExcelProperty("跟单比例")
	private BigDecimal followParam;

	@ExcelProperty("跟单状态0-未开启 1-已开启")
	private Integer followStatus;

	@ExcelProperty("跟单开仓状态 0-未开启 1-开启")
	private Integer followOpen;

	@ExcelProperty("跟单平仓状态 0-未开启 1-开启")
	private Integer followClose;

	@ExcelProperty("跟单补单状态 0-未开启 1-开启")
	private Integer followRep;

	@ExcelProperty("下单方式")
	private Integer placedType;

	@ExcelProperty("止盈止损0-不跟随 1-跟随")
	private Integer tpSl;

	@ExcelProperty("跟单方向0-正向1-反向")
	private Integer followDirection;

	@ExcelProperty("暂停订阅0-否 1-是")
	private Integer pause;

	@ExcelProperty("备注")
	private String remark;

	@ExcelProperty("订单量")
	private Integer orderNum;

}