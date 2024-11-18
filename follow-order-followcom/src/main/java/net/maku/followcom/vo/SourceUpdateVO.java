package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * Author:  zsd
 * Date:  2024/11/15/周五 15:14
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "喊单更新")
public class SourceUpdateVO extends SourceInsertVO {
    @Serial
    private static final long serialVersionUID = -162047724090123785L;
    @JsonProperty("Id")
    @NotNull(message = "Id不能为空")
    private Long id;
}
