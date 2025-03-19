package net.maku.mascontrol.vo;

import lombok.Builder;
import lombok.Data;
import net.maku.followcom.vo.OrderRepairInfoVO;

import java.io.Serializable;
import java.util.List;


@Data
@Builder
public class MasterRepairVO implements Serializable {

    private List<OrderRepairInfoVO> pageData;

    //信号源账号
    private Integer masterAccount;

    //信号源服务器
    private String masterPlatform;

    //信号源漏单数量
    private Integer repairNum;

    /**
     * 跟单状态
     */
    private Integer followStatus;

    /**
     * 连接状态，0为异常，1为正常
     */
    private Integer status;
}
