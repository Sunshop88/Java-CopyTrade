package net.maku.followcom.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.fhs.core.trans.vo.TransPojo;
import lombok.Data;

import java.util.Date;

/**
 * 测速记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class FollowTestSpeedExcelVO implements TransPojo {
	private Integer id;

	@ExcelProperty("测速时间")
	private Date doTime;

	@ExcelProperty("测试状态0-失败 1-进行中 2-成功")
	private Integer status;

	@ExcelProperty("测速人")
	private String testName;

}