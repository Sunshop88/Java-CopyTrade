package net.maku.followcom.query;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;

/**
 * Author:  zsd
 * Date:  2025/1/2/周四 15:16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class SymbolAnalysisQuery extends Query {

    /**
     * mt4账号
     */
    private String account;
    /**
     * vpsId
     */
    private Long vpsId;

    /**
     * 平台服务器
     */
    private String platform;

    /**
     * 信号源账号
     */

    private String sourceAccount;

    /**
     * 信号源服务器
     */
    private String sourcePlatform;
    /**
     * 账号类型
     */
    private Integer type;
}
