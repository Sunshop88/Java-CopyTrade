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

    @Schema(description = "排序字段，默认服务器名称排序（prop1：服务器名称，prop3：账号数量，prop4：非默认节点数量）")
    @Schema(description = "默认节点")
    private String defaultNode;

    @Schema(description = "排序字段，默认服务器名称排序")
    String order = "prop1";

    @Schema(description = "是否升序，默认升序")
    boolean asc = true;
}
