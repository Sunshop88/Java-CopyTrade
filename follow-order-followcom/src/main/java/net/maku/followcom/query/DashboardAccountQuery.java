package net.maku.followcom.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;

/**
 * Author:  zsd
 * Date:  2025/1/2/周四 10:20
 * 仪表盘-账号数据监控-账号数据查询
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "仪表盘-账号数据监控-账号数据查询")
public class DashboardAccountQuery extends Query {
    //劵商名称
    private  String brokerName;
}
