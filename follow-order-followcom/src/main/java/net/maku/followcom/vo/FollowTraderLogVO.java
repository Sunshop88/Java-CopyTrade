package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import lombok.Data;
import java.io.Serializable;
import net.maku.framework.common.utils.DateUtils;
import java.util.Date;

/**
 * 交易日志
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "交易日志")
public class FollowTraderLogVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;

	@Schema(description = "日志记录")
	private String logDetail;

	@Schema(description = "vps名称")
	private String vpsName;

	@Schema(description = "vpsId")
	private Integer vpsId;

	@Schema(description = "账号类型0-信号源 1-跟单者")
	private Integer traderType;

	@Schema(description = "类型0-新增 1-编辑 2-删除 3-下单 4-平仓 5-补单")
	private Integer type;

	@Schema(description = "是否主动0-否 1-是")
	private Integer ifInitiative;

	@Schema(description = "单号")
	private Integer orderNo;

	@Schema(description = "版本号")
	private Integer version;

	@Schema(description = "删除标识  0：正常   1：已删除")
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