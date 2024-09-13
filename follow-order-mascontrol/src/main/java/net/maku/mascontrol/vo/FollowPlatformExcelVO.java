package net.maku.mascontrol.vo;

import lombok.Data;
import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;

/**
 * 平台管理
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowPlatformExcelVO implements TransPojo {
	@ExcelProperty("ID")
	private Long id;

	@ExcelProperty("券商名称")
	private String brokerName;

	@ExcelProperty("平台类型")
	private String platformType;

	@ExcelProperty("服务器")
	private String server;

	@ExcelProperty("服务器节点")
	private String serverNode;

	@ExcelProperty("备注")
	private String remark;

}