package net.maku.followcom.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;

import java.util.List;

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
    private String brokerName;
   //服务器
    private String server;
    // vps
    private String vpsName;
     //账号
    private String account;
    //信号源
    private String sourceAccount;
    private List<Integer> vpsIds;
}
