package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Author:  zsd
 * Date:  2025/2/14/周五 18:40
 */
@Data
@Schema(description = "redis漏单缓存")
public class RepairCacheVO {
  @JsonProperty("SourceId")
  private  Long  sourceId;
    @JsonProperty("FollowId")
    private  Long  followId;
    @JsonProperty("SourceUser")
    private  Long  sourceUser;
    @JsonProperty("FollowUser")
    private  Long  followUser;

    @JsonProperty(value = "Open")
    private List<OrderCacheVO> open;
    @JsonProperty(value = "Close")
    private List<OrderCacheVO> close;

}
