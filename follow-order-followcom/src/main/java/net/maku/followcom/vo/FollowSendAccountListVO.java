package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import net.maku.followcom.entity.FollowSendAccountEntity;

import java.io.Serializable;
import java.util.List;

/**
 * 下单账户列表
 */
@Data
@Schema(description = "下单账户列表")
public class FollowSendAccountListVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;

	@Schema(description = "MT4账号")
	private String account;

	@Schema(description = "跟单账号")
	private List<FollowSendAccountEntity> followSendAccountEntityList;

}