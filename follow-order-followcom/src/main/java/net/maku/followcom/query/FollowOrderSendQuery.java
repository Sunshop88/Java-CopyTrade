package net.maku.followcom.query;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;
import org.hibernate.validator.constraints.Range;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 下单记录查询
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "下单记录查询")
public class FollowOrderSendQuery extends Query {

    @Schema(description = "账户id")
    private Integer traderId;

    @Schema(description = "是否滑点详情")
    private Integer flag;

    @Schema(description = "账户")
    private String account;

    @Schema(description = "平台名称")
    private String platform;

    @Schema(description = "品种名称")
    private String symbol;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "下单时间-开始")
    private String startTime;

    @Schema(description = "下单时间-结束")
    private String endTime;

    @Schema(description = "券商")
    private String brokeName;

    @Schema(description = "平仓时间-开始")
    private String closeStartTime;

    @Schema(description = "平仓时间-结束")
    private String closeEndTime;

    @Schema(description = "账户集合")
    private List<Long> traderIdList;

    @Schema(description = "下单方式")
    private Integer placedType;

    @Schema(description = "订单号")
    private String sendNo;

    private String serverIp;

    @Schema(description = "平仓id")
    private Integer closeId;

    @Schema(description = "魔术号")
    private Integer magical;

    @Schema(description = "开仓VPS名称")
    private String serverName;

    @Schema(description = "平仓VPS名称")
    private String closeServerName;

    @Schema(description = "开仓请求时间-开始")
    private String requestOpenTimeStart;

    @Schema(description = "开仓请求时间-结束")
    private String requestOpenTimeEnd;

    @Schema(description = "平仓请求时间-开始")
    private String requestCloseTimeStart;

    @Schema(description = "平仓请求时间-结束")
    private String requestCloseTimeEnd;

    //喊单账号
    @Schema(description = "喊单账号")
    private  String sourceUser;

    /**
     * 是否外部0-否 1-是
     */
    @Schema(description = "是否外部0-否 1-是")
    private  Integer isExternal;

    @Schema(description = "是否平仓详情0-否 1-是")
    private  Integer isClose;


}