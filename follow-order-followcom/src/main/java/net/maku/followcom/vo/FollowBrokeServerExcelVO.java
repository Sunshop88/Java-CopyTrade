package net.maku.followcom.vo;

import lombok.Data;
import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;

/**
 * 导入服务器列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowBrokeServerExcelVO implements TransPojo {
	private Integer id;

	@ExcelProperty("服务器名称")
	private String serverName;

	@ExcelProperty("服务器节点ip")
	private String serverNode;

	@ExcelProperty("服务器节点端口")
	private String serverPort;

}