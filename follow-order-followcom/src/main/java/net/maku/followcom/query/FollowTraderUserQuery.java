package net.maku.followcom.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 账号初始表查询
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "账号初始表查询")
public class FollowTraderUserQuery extends Query {
    @Schema(description = "上传文件id")
    private int uploadId;

    @Schema(description = "添加账号状态 0：成功 1：失败")
    private int uploadStatus;
}