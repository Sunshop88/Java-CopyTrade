package net.maku.subcontrol.vo;

import lombok.Builder;
import lombok.Data;
import net.maku.followcom.vo.FollowVpsInfoVO;
import net.maku.followcom.vo.FollowVpsVO;
import net.maku.framework.common.utils.PageResult;

import java.io.Serializable;

/**
 * Author:  zsd
 * Date:  2024/12/6/周五 19:06
 */
@Data
@Builder
public class VpsDataVo implements Serializable {

    private PageResult<FollowVpsVO> pageData;

    private FollowVpsInfoVO vpsInfoData;
}
