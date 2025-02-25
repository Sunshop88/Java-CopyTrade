package net.maku.followcom.vo;

import lombok.Data;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;
import com.fhs.core.trans.anno.Trans;
import com.fhs.core.trans.constant.TransType;
import java.util.Date;

/**
 * 账号初始表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowTraderUserExcelVO implements TransPojo {
	private Long id;

	@ExcelProperty("账号")
	private String account;

	@ExcelProperty("密码")
	private String password;

	@ExcelProperty("平台id")
	private Integer platformId;

	@ExcelProperty("平台服务器")
	private String platform;

	@ExcelProperty("券商名称")
	private String brokerName;

	@ExcelProperty("挂靠vps 如:IP-名称-挂靠类型")
	private String uploadStatusName;

	@ExcelProperty("账号类型")
	private String accountType;

	@ExcelProperty("服务器节点")
	private String serverNode;

	@ExcelProperty("组别名称")
	private String groupName;

	@ExcelProperty("组别id")
	private Integer groupId;

	@ExcelProperty("挂靠状态0-未挂靠 1-已挂靠")
	private Integer status;

	@ExcelProperty("备注")
	private String remark;

	@ExcelProperty("上传文件id")
	private Integer uploadId;

	@ExcelProperty("添加账号状态 0：成功 1：失败")
	private Integer upload_status;

}