package net.maku.followcom.vo;

import lombok.Data;

import java.util.List;

@Data
public class FollowUpdatePasswordVO {
    private List<FollowTraderUserVO> voList;
    private String password;
    private String confirmPassword;
}
