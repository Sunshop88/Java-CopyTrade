package net.maku.followcom.vo;

import lombok.Data;

import java.util.List;

@Data
public class FollowBatchUpdateVO {
    private List<FollowTraderUserVO> voList;
    private String password;
    private String confirmPassword;
    private List<Long> idList;
    private long groupId;
    private long id;
}
