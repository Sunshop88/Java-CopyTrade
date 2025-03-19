package net.maku.followcom.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 平仓记录查询
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "平仓记录查询")
public class FollowOrderCloseQuery extends Query {
    @Schema(description = "平仓时间-开始")
    private String startTime;

    @Schema(description = "平仓时间-结束")
    private String endTime;
}