package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import lombok.Data;
import java.io.Serializable;
import net.maku.framework.common.utils.DateUtils;
import java.util.Date;

/**
 * 上传账号记录表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "上传账号记录表")
public class FollowUploadTraderUserVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "ID")
	private Long id;

	@Schema(description = "文件上传时间")
	private Date uploadTime;

	@Schema(description = "操作人")
	private String operator;

	@Schema(description = "状态 0：处理中 1：处理完成")
	private Integer status;

	@Schema(description = "上传数据数量")
	private Long uploadTotal;

	@Schema(description = "成功数量")
	private Long successCount;

	@Schema(description = "失败数量")
	private Long failureCount;

	@Schema(description = "版本号")
	private Integer version;

	@Schema(description = "删除标识 0：正常 1：已删除")
	private Integer deleted;

	@Schema(description = "创建者")
	private Long creator;

	@Schema(description = "创建时间")
	private Date createTime;

	@Schema(description = "更新者")
	private Long updater;

	@Schema(description = "更新时间")
	private Date updateTime;

}