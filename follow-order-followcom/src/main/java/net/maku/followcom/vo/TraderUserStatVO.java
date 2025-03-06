package net.maku.followcom.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.maku.framework.common.utils.PageResult;

import java.math.BigDecimal;

/**
 * Author:  zsd
 * Date:  2025/2/26/周三 18:12
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TraderUserStatVO {
    //账号总数量
    private int total;
    //未挂靠vps
    private int noVpsNum;
    //连接成功
    private int conNum;
    //连接异常
    private int errNum;


   private PageResult<FollowTraderUserVO> pageResult;
}
