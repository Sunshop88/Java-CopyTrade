package net.maku.followcom.entity;

import lombok.Data;

import java.util.List;

@Data
public class MeasureRequestEntity {
    private List<String> servers;
    private List<String> vps;
    private FollowVpsEntity vpsEntity;
    private Integer testId;
//    private FollowTestSpeedVO overallResult;
}
