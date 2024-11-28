package net.maku.followcom.query;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;

/**
 * vps列表查询
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "vps列表查询")
public class FollowVpsQuery extends Query {
    /**
     * 名称
     */
    private String name;
    /**
     * 是否状态，0为停止，1为运行
     */
    private Integer isActive;
}