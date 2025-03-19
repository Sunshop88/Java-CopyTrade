package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "修改服务器名称")
public class FollowTestServerNameVO {
    @Schema(description = "服务器新名称")
    private String newName;

    @Schema(description = "服务器旧名称")
    @NotBlank(message = "服务器旧名称不能为空")
    private String oldName;
}
