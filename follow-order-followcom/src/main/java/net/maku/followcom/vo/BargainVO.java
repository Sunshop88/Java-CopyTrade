package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Author:  zsd
 * Date:  2025/2/24/周一 16:58
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BargainVO {
    //持仓概览
    private BargainOverviewVO bargainOverviewVO;
    //账号列表
     private  BargainAccountVO accountVO;
    //持仓订单集合
    private List<OrderActiveInfoVO> orderActiveInfoList;

}
