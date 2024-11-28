package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "图片上传")
public class FollowLogoUploadVO {
    private static final long serialVersionUID = 1L;

    @Schema(description = "文件名称")
    private String name;

    @Schema(description = "文件地址")
    private String url;

    @Schema(description = "文件大小")
    private Long size;
}
