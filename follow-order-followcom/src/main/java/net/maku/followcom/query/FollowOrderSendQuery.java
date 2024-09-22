package net.maku.followcom.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 下单记录查询
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "下单记录查询")
public class FollowOrderSendQuery extends Query {

    @Schema(description = "账户id")
    private Integer traderId;

    @Schema(description = "账户")
    private String account;

    @Schema(description = "平台名称")
    private String platForm;

    @Schema(description = "品种名称")
    private String symbol;
}