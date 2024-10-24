package net.maku.followcom.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 平仓
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "平仓")
public class FollowOrderCloseVO implements Serializable {
	private Integer id;

	@Schema(description = "品种类型")
	private String symbol;

	@Schema(description = "账号")
	private String account;

	@Schema(description = "账号id")
	private Integer traderId;

	@Schema(description = "类型0-buy 1-sell 2-buy&sell")
	private Integer type;

	@Schema(description = "总单数")
	private Integer totalNum;

	@Schema(description = "成功单数")
	private Integer successNum;

	@Schema(description = "失败单数")
	private Integer failNum;

	@Schema(description = "间隔时间 毫秒")
	private Integer intervalTime;

	@Schema(description = "状态0-进行中 1-已完成")
	private Integer status;

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

	@Schema(description = "券商")
	private String brokeName;

	@Schema(description = "服务器")
	private String server;

	@Schema(description = "vps地址")
	private String ipAddr;

	@Schema(description = "vps名称")
	private String serverName;

	@Schema(description = "完成时间")
	private LocalDateTime finishTime;

}