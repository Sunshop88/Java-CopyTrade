package net.maku.followcom.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 上传账号记录表查询
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "上传账号记录表查询")
public class FollowUploadTraderUserQuery extends Query {
    @Schema(description = "类型 0：新增账号 1：修改密码")
    private Integer type;
}