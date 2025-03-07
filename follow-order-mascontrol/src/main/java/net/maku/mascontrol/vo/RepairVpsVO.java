package net.maku.mascontrol.vo;

import com.baomidou.mybatisplus.annotation.TableField;
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

    /**
     * 是否状态，0为停止，1为运行
     */
    private Integer isActive;

    /**
     * 连接状态，0为异常，1为正常
     */
    private Integer connectionStatus;

}
