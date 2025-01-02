package net.maku.followcom.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Author:  zsd
 * Date:  2025/1/2/周四 17:41
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RankVO {
    //账号
    private String account;
    //平台
    private String platform;
    //盈利
    private BigDecimal profit;
}
