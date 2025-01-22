package net.maku.followcom.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FollowSendAccountEntity {
    private Long id;

    @Schema(description = "MT4账号")
    private String account;

}
