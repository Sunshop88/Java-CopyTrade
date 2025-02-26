package net.maku.followcom.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Author:  zsd
 * Date:  2025/2/24/周一 18:04
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BargainAccountVO {

    //账号列表 数量
    private Integer accountNum;
   //账号连接
    private Integer accountConnectedNum;
    //异常数量
    private Integer accountDisconnectedNum;
    //总可用款
    private BigDecimal paragraph;
    private List<OrderActiveInfoVO> orderActiveInfoList;
}
