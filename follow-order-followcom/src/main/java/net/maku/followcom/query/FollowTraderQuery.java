package net.maku.followcom.query;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * mt4账号查询
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "mt4账号查询")
public class FollowTraderQuery extends Query {
    private String serverIp;

    private String account;

    private Long traderId;

    /**
     * 类型0-信号源 1-跟单者
     */
    private Integer type;

    private List<Long> traderList;

}