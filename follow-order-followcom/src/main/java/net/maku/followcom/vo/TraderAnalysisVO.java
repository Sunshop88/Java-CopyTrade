package net.maku.followcom.vo;

import lombok.Data;

/**
 * Author:  zsd
 * Date:  2025/1/2/周四 16:56
 */
@Data
public class TraderAnalysisVO {
    /**
     * 品种
     * */
    private String symbol;
    /**
     * mt4账号
     */
    private String account;
    /**
     * vpsId
     */
    private String vpsId;

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

    private String order;

    private Boolean asc;
}
