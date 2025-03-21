package net.maku.followcom.vo;

import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class FollowBatchUpdateVO {
    private List<FollowTraderUserVO> voList;
    private String password;
    private String confirmPassword;
    private Set<Long> idList;
    private long groupId;
    private long id;
}
