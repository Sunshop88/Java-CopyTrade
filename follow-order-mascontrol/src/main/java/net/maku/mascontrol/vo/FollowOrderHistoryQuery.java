package net.maku.mascontrol.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;

/**
 * 所有MT4账号的历史订单查询
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "所有MT4账号的历史订单查询")
public class FollowOrderHistoryQuery extends Query {

    @Schema(description = "账号ID")
    @NotEmpty(message = "账号id不能为空")
    private Long traderUserId;

    @Schema(description = "账号ID")
    private Long traderId;

    @Schema(description = "时间-开始")
    private String startTime;

    @Schema(description = "时间-结束")
    private String endTime;
    @Schema(description = "0-bug 1-sell 6出入金 7信用")
    private Integer type;
    @Schema(description = "0-bug 1-sell 6出入金 7信用")
    private Integer status;


    private Integer vpsId;
}