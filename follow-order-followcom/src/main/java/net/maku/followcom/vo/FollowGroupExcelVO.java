package net.maku.followcom.vo;

import lombok.Data;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;
import com.fhs.core.trans.anno.Trans;
import com.fhs.core.trans.constant.TransType;
import java.util.Date;

/**
 * 组别
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowGroupExcelVO implements TransPojo {
	@ExcelProperty("ID")
	private Integer id;

	@ExcelProperty("组别名称")
	private String name;

	@ExcelProperty("账号数量")
	private Integer number;

	@ExcelProperty("颜色")
	private String color;

}