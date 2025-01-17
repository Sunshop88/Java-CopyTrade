package net.maku.mascontrol.vo;

import lombok.Builder;
import lombok.Data;
import net.maku.followcom.vo.FollowVpsInfoVO;
import net.maku.followcom.vo.FollowVpsVO;
import net.maku.framework.common.utils.PageResult;

import java.io.Serializable;
import java.util.List;


@Data
@Builder
public class RepairDataVo implements Serializable {

    private List<RepairVpsVO> pageData;

    //总漏单数量
    private Integer total;

    //漏单VPS
    private Integer vpsNum;

    //漏单信号源
    private Integer masterNum;

    //漏单跟单账号
    private Integer slaveNum;

    /**
     * 漏单的 vps数量
     * **/
    private Integer vpsActiveNum;
    /**
     * 漏单的 信号源数量
     * **/
    private Integer sourceActiveNum;
    /**
     * 漏单的 跟单源数量
     * **/
    private Integer followActiveNum;
}
