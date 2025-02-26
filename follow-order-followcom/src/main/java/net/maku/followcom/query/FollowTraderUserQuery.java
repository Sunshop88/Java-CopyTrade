package net.maku.followcom.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.List;

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

    @Schema(description = "账号")
    private String account;

    @Schema(description = "平台")
    private String platform;

    @Schema(description = "券商名称")
    private String brokerName;

    @Schema(description = "组别")
    private List<String> groupName;

    @Schema(description = "挂靠类型")
    private String uploadStatusName;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "挂号的vps")
    private List<Integer> vpsIds;

    @Schema(description = "连接状态")
    private List<Integer> status;



}