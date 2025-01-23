package net.maku.followcom.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单详情查询
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "订单详情查询")
public class FollowOrderDetailQuery extends Query {
    @Schema(description = "账号ID")
    private Long traderId;

    @Schema(description = "时间-开始")
    private String startTime;

    @Schema(description = "时间-结束")
    private String endTime;

    private Integer type;

    private Boolean isHistory=false;
}