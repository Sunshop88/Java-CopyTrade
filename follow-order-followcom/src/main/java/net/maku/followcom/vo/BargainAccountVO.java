package net.maku.followcom.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.maku.framework.common.utils.PageResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Author:  zsd
 * Date:  2025/2/24/周一 18:04
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BargainAccountVO {

    List<List<BigDecimal>> statList;
    //账号列表 数量
    private Integer accountNum;
   //账号连接
    private Integer accountConnectedNum;
    //异常数量
    private Integer accountDisconnectedNum;
    //总可用款
    private BigDecimal paragraph;
    //持仓概览统计

    //账号列表
   private PageResult<FollowTraderUserVO>  traderUserPage;
    private List<OrderActiveInfoVO> orderActiveInfoList;
}
