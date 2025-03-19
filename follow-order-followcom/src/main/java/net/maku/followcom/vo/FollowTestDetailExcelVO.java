package net.maku.followcom.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;
import lombok.Data;

/**
 * 测速详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowTestDetailExcelVO implements TransPojo {
	private Long id;

	@ExcelProperty("服务器id")
	private Integer serverId;

	@ExcelProperty("服务器名称")
	private String serverName;

	@ExcelProperty("平台类型MT4/MT5")
	private String platformType;

	@ExcelProperty("测速id")
	private Integer testId;

	@ExcelProperty("服务器节点")
	private String serverNode;

	@ExcelProperty("速度ms")
	private Integer speed;

}