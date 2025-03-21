package net.maku.followcom.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 品种匹配
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FollowVarietyExcelVO implements TransPojo {
	@ExcelProperty("ID")
	private Integer id;

	@ExcelProperty("标准合约")
	private Integer stdContract;

	@ExcelProperty("品种名称")
	private String stdSymbol;

	@ExcelProperty("券商名称")
	private String brokerName;

	@ExcelProperty("券商对应的品种名称")
	private String brokerSymbol;

	@ExcelProperty("模板ID")
	@NotNull(message = "模板ID不能为空")
	private Integer templateId;

	@ExcelProperty("模板名称")
	@NotBlank(message = "模板名称不能为空")
	private String templateName;

}