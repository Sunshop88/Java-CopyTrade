package net.maku.mascontrol.vo;

import lombok.Builder;
import lombok.Data;
import net.maku.followcom.vo.FollowVpsVO;

import java.io.Serializable;
import java.util.List;


@Data
@Builder
public class RepairVpsVO implements Serializable {

    private List<MasterRepairVO> pageData;

    //漏单数量
    private Integer repairNum;

    //VPS名称
    private String vpsName;

}
