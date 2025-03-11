package net.maku.followcom.enums;

import lombok.Getter;

@Getter
public enum TraderUserTypeEnum {
    //类型 0：新增账号 1：修改密码 2：挂靠VPS 3:上传默认节点
    ADD_ACCOUNT(0),
    MODIFY_PASSWORD(1),
    ATTACH_VPS(2),
    UPLOAD_DEFAULT_NODE(3);

    private Integer type;

    TraderUserTypeEnum(Integer type) {
        this.type = type;
    }
}
