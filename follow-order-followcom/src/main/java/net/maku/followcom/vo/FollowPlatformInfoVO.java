package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;

@Data
@Schema(description = "平台信息")
public class FollowPlatformInfoVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "券商数量")
    private Integer brokerNum;

    @Schema(description = "服务器数量")
    private Integer serverNum;

}
