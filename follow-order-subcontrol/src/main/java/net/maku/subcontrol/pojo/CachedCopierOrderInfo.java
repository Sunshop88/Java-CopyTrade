package net.maku.subcontrol.pojo;

import com.cld.utils.date.ThreeStrategyDateUtil;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;
import net.maku.followcom.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.rule.Comment;
import online.mtapi.mt4.Order;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 书名
 */
@Data
public class CachedCopierOrderInfo implements Serializable {

    /**
     * 跟单者开仓订单号
     */
    Long slaveTicket;

    String slaveSymbol;

    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    LocalDateTime openTime;

    Double openPrice;

    /**
     * 开仓类型 做多 做空
     */
    Integer slaveType;

    /**
     * 当前持仓
     */
    Double slavePosition;

    /**
     * 开仓时的开仓比例
     */
    Double ratio;

    public CachedCopierOrderInfo() {
    }

    public CachedCopierOrderInfo(Order order) {
        slaveTicket = (long) order.Ticket;
        slaveSymbol = order.Symbol;
        openTime = order.OpenTime;
        this.openPrice = order.OpenPrice;
        slaveType = order.Type.getValue();
        slavePosition = order.Lots;
    }

    public CachedCopierOrderInfo(FollowSubscribeOrderEntity openOrderMapping) {
        if (Pattern.matches(Comment.toSharpRegex, openOrderMapping.getSlaveComment())) {
            try {
                slaveTicket = Long.parseLong(openOrderMapping.getSlaveComment().substring(4));
            } catch (NumberFormatException e) {
                e.printStackTrace();
                slaveTicket = Long.valueOf(openOrderMapping.getSlaveTicket());
            }
        } else {
            slaveTicket = Long.valueOf(openOrderMapping.getSlaveTicket());
        }

        slaveSymbol = openOrderMapping.getSlaveSymbol();
        openTime = openOrderMapping.getSlaveOpenTime();
        // TODO: 2023/5/30 开仓价格需要保存
        openPrice = openOrderMapping.getSlaveOpenPrice().doubleValue();
        slaveType = openOrderMapping.getSlaveType();
        slavePosition = openOrderMapping.getSlaveLots().doubleValue();
//        ratio = openOrderMapping.getRatio().doubleValue();
    }


    public CachedCopierOrderInfo(CachedCopierOrderInfo cachedCopierOrderInfo) {

    }
}