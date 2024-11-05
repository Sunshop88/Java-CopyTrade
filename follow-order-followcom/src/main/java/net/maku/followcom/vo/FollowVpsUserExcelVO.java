package net.maku.followcom.vo;

import lombok.Data;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;
import com.fhs.core.trans.anno.Trans;
import com.fhs.core.trans.constant.TransType;
import java.util.Date;

/**
 * 用户vps可查看列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowVpsUserExcelVO implements TransPojo {
	private Long id;

	@ExcelProperty("用户id")
	private Integer userId;

	@ExcelProperty("vpsId")
	private Integer vpsId;

	@ExcelProperty("vps服务器名称")
	private String vpsName;

}