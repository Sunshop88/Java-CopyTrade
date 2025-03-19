package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Author:  zsd
 * Date:  2024/11/15/周五 15:28
 */
@Data
@Schema(description = "喊单删除")
public class SourceDelVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 630045799655058400L;
    //vps服务器id
    @JsonProperty("ClientId")
    private Integer serverId;
    //账号id
    @JsonProperty("Id")
    @NotNull(message = "Id不能为空")
    private Long id;
}
