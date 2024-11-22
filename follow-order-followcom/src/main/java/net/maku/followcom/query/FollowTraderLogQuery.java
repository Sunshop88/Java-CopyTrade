package net.maku.followcom.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;

/**
 * 交易日志查询
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "交易日志查询")
public class FollowTraderLogQuery extends Query {

    //操作人
    private String realName;
    //vps名称
    private String vpsName;
    //记录
    private String logDetail;
    //类型0-新增 1-编辑 2-删除 3-下单 4-平仓 5-补单
    private Integer type;

}