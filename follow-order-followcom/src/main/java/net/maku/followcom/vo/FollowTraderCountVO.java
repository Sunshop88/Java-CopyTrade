package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;


@Data
public class FollowTraderCountVO implements Serializable {

	@Schema(description = "平台服务器")
	private String serverName;

	@Schema(description = "账号数量")
	private String accountCount;

	@Schema(description = "券商名称")
	private String brokerName;

	@Schema(description = "默认服务器数量")
	private String defaultServerAccount;

	@Schema(description = "服务对应的节点数量")
	private Integer serverNodeCount;

	@Schema(description = "默认服节点")
	private String defaultServerNode;

	@Schema(description = "默认节点数量")
	private Integer nodeCount;
}