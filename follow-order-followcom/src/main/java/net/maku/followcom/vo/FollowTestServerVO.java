package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Schema(description = "添加服务器节点")
public class FollowTestServerVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "服务器id")
    @NotNull(message = "服务器ID不能为空")
    @Min(value = 1, message = "服务器ID必须大于等于1")
    private Integer serverId;

    @Schema(description = "服务器名称")
    @NotBlank(message = "服务器名称不能为空")
    @Size(max = 100, message = "服务器名称长度不能超过100个字符")
    private String serverName;

    @Schema(description = "平台类型MT4/MT5")
    @NotBlank(message = "平台类型不能为空")
    private String platformType;

    @Schema(description = "服务器节点列表")
    @NotBlank(message = "服务器节点列表不能为空")
    private List<String> serverNodeList;
}
