package net.maku.followcom.query;

import com.alibaba.fastjson.JSONArray;
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

    @Schema(description = "连接状态 0正常 1错误 2异常")
    private List<Integer> status;
    @Schema(description = "账号类型 0策略 1跟单 2交易分配 不传全部")
    private List<Integer> accountType;
    @Schema(description = "组别")
    private List<Integer> groupIds;

    private JSONArray accountVos;

    @Schema(description = "挂靠状态")
    private Integer hangStatus;




}