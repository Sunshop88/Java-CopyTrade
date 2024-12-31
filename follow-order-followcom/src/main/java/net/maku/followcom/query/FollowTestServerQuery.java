package net.maku.followcom.query;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;

/**
 * 服务器管理查询
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "服务器管理查询")
public class FollowTestServerQuery extends Query {
    @Schema(description = "券商名称")
    private String brokerName;

    @Schema(description = "服务器名称")
    private String serverName;

    @Schema(description = "服务器节点")
    private String serverNode;
}
