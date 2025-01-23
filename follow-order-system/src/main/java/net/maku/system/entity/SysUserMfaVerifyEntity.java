package net.maku.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 用户MFA认证
 * </p>
 *
 * @author Calorie
 * @since 2025-01-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_user_mfa_verify")
@Schema(description="用户MFA认证")
public class SysUserMfaVerifyEntity extends Model<SysUserMfaVerifyEntity> {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "用户id")
    private Long userId;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "秘钥")
    private String secretKey;

    @Schema(description = "二维码")
    private String qrCode;

    @Schema(description = "是否MFA认证(1：已认证；0：未认证)")
    private Integer isMfaVerified;

    @Schema(description = "是否已删除（0：否；1：已删除）")
    private Integer isDeleted;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建日期")
    private Date createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新日期")
    private Date updateTime;


    @Override
    public Serializable pkVal() {
        return this.id;
    }

}
