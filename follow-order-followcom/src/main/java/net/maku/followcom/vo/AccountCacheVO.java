package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Author:  zsd
 * Date:  2024/11/19/周二 10:20
 * 推送到redis实体
 */
@Data
@Schema(description = "redis缓存")
public class AccountCacheVO {
    //s喊单开头+id 跟单f+id
    @JsonProperty("Key")
    private String key;
    //  id+user
    @JsonProperty("Group")
    private String group;
    //  SOURCE喊单 FOLLOW跟单
    @JsonProperty("Type")
    private String type;
    //
    @JsonProperty("TypeString")
    private String typeString;
    //  平台类型
    @JsonProperty("PlatformType")
    private String platformType;
    //  备注
    @JsonProperty("Comment")
    private String comment;
    //  id
    @JsonProperty("Id")
    private Long id;
    //  平台
    @JsonProperty("PlatformName")
    private String platformName;
    //  平台
    @JsonProperty("User")
    private Integer user;
    //余额
    @JsonProperty(value = "Balance")
    private BigDecimal balance;
    //净值
    @JsonProperty(value = "Equity")
    private BigDecimal equity;
    //
    @JsonProperty(value = "Credit")
    private Double credit;
    //
    @JsonProperty(value = "Profit")
    private Double profit;
    //
    @JsonProperty(value = "ProfitPercentage")
    private BigDecimal profitPercentage;
    //可用预付款
    @JsonProperty(value = "FreeMargin")
    private Double freeMargin;
    //  是对margin_proportion
    @JsonProperty(value = "Margin")
    private BigDecimal margin;
    //
    @JsonProperty(value = "MarginLevel")
    private BigDecimal marginLevel;
    //是对 leverage
    @JsonProperty(value = "Leverage")
    private BigDecimal leverage;
    //持仓数量
    @JsonProperty(value = "Count")
    private Integer count;
    //
    @JsonProperty(value = "Lots")
    private Double lots;
    //
    @JsonProperty(value = "Buy")
    private Double buy;

    @JsonProperty(value = "Sell")
    private Double sell;

    @JsonProperty(value = "ModeString")
    private String modeString;
    @JsonProperty(value = "PlacedTypeString")
    private String placedTypeString;
    @JsonProperty(value = "Server")
    private String server;
    @JsonProperty(value = "DefaultServer")
    private Boolean defaultServer;
    @JsonProperty(value = "ManagerStatus")
    private String managerStatus;
    @JsonProperty(value = "Status")
    private Boolean status;
    @JsonProperty(value = "Password")
    private String password;
    @JsonProperty(value = "PlatformId")
    private Integer platformId;
    @JsonProperty(value = "Orders")
    private List<OrderCacheVO> Orders;

}
