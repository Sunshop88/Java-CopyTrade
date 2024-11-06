package net.maku.mascontrol.entity;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.mascontrol.vo.FollowTestSpeedVO;

import java.util.List;

@Data
public class MeasureRequestEntity {
    private List<String> servers;
    private List<String> vps;
    private FollowVpsEntity vpsEntity;
    private Integer testId;
//    private FollowTestSpeedVO overallResult;
}
