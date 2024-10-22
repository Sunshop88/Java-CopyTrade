package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 品种匹配
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(description = "品种匹配")
public class FollowVarietyVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "ID")
	private Integer id;

	@Schema(description = "标准合约")
	private Integer stdContract;

	@Schema(description = "品种名称")
	private String stdSymbol;

	@Schema(description = "券商名称")
	private String brokerName;

	@Schema(description = "券商对应的品种名称")
	private String brokerSymbol;

	@Schema(description = "版本号")
	private Integer version;

	@Schema(description = "删除标识 0：正常 1：已删除")
	private Integer deleted;

	@Schema(description = "创建者")
	private Long creator;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "更新者")
	private Long updater;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

}