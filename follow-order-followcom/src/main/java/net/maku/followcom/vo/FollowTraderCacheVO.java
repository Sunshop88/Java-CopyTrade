package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * Author:  zsd
 * Date:  2024/11/19/周二 10:20
 * 推送到redis实体
 */
@Data
@Schema(description = "redis缓存")
public class FollowTraderCacheVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 2170271124248269092L;
    @JsonProperty("Accounts")
    private List<AccountCacheVO> accounts;
    @JsonProperty("UpdateAt")
    private Date updateAt;
    @JsonProperty("Status")
    private Boolean status;
}
