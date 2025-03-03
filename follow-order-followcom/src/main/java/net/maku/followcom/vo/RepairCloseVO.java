package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Author:  zsd
 * Date:  2025/3/3/周一 9:54
 */
@Data
@Schema(description = "一键漏平")
public class RepairCloseVO {


    @Schema(description = "跟单者的ID")
    private Long slaveId;
    @Schema(description = "订单号")
    private Integer orderNo;
    @Schema(description = "vpsId")
    private Integer vpsId;
}
