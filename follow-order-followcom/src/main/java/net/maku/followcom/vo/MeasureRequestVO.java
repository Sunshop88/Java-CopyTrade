package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.maku.followcom.entity.FollowVpsEntity;

import java.util.List;


/**
 * vps测速请求
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "vps测速请求")
public class MeasureRequestVO {

    @Schema(description = "服务器列表")
    @NotNull(message = "服务器列表至少包含一个元素")
    private List<String> servers;

    @Schema(description = "vps列表")
    @NotNull(message = "vps列表至少包含一个元素")
    private List<String> vps;

    @Schema(description = "vps实体")
    private FollowVpsEntity vpsEntity;

    @Schema(description = "测速任务id")
    private Integer testId;

    @Schema(description = "到期时间")
    private String ExpiryDateStr;
}
