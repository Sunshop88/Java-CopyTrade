package net.maku.followcom.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Author:  zsd
 * Date:  2025/2/26/周三 14:24
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VpsDescVO {
    //描述
    private String desc;
    //链接状态
    private Integer status;
    //异常信息
    private String statusExtra;


    private String forex;

    private String cfd;

    public Long traderId;

    public  String ipAddress;
    private Long sourceId;
    private String sourceAccount;
    private String sourceName;
}
