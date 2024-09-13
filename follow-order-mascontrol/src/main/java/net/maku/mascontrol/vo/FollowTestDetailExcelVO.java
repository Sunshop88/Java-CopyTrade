package net.maku.mascontrol.vo;

import lombok.Data;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;
import com.fhs.core.trans.anno.Trans;
import com.fhs.core.trans.constant.TransType;
import java.util.Date;

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
	private String severNode;

	@ExcelProperty("速度ms")
	private Integer speed;

}