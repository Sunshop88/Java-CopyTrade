package net.maku.mascontrol.vo;

import lombok.Data;
import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;
import java.util.Date;

/**
 * vps列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowVpsExcelVO implements TransPojo {
	private Integer id;

	@ExcelProperty("机器码")
	private String clientId;

	@ExcelProperty("名称")
	private String name;

	@ExcelProperty("ip地址")
	private String ipAddress;

	@ExcelProperty("到期时间")
	private Date expiryDate;

	@ExcelProperty("备注")
	private String remark;

	@ExcelProperty("是否对外开放，0为否，1为是")
	private Integer isOpen;

	@ExcelProperty("是否状态，0为停止，1为运行")
	private Integer isActive;

	@ExcelProperty("连接状态，0为异常，1为正常")
	private Integer connectionStatus;

	@ExcelProperty("租户ID")
	private Long tenantId;

}