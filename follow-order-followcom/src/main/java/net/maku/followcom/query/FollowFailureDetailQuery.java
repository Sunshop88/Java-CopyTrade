package net.maku.followcom.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;
import org.springframework.format.annotation.DateTimeFormat;


/**
 * 失败详情表查询
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "失败详情表查询")
public class FollowFailureDetailQuery extends Query {
    @Schema(description = "记录id")
    private int recordId;

    @Schema(description = "类型 0：新增账号 1：修改密码 2：挂靠VPS")
    private int type;
}