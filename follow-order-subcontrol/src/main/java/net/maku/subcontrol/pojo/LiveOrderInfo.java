package net.maku.subcontrol.pojo;

import com.alibaba.fastjson.annotation.JSONField;
import com.cld.utils.date.ThreeStrategyDateUtil;
import lombok.Data;
import online.mtapi.mt4.Order;

import java.io.Serializable;
import java.util.Date;

@Data
public class LiveOrderInfo implements Serializable {

    private long ticket;
    private int type;
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private Date openTime;
    private double lots;
    private double openPrice;
    private double closePrice;
    private double sl;
    private double tp;
    private double profit;
    private double commission;
    private double swap;
    private String symbol;
    private String comment;

    public LiveOrderInfo(Order orderInfo){
        this.ticket = orderInfo.Ticket;
        this.type = orderInfo.Type.getValue();
        this.openTime = ThreeStrategyDateUtil.localDateTime2Date(orderInfo.OpenTime);
        this.lots = orderInfo.Lots;
        this.openPrice = orderInfo.OpenPrice;
        this.closePrice = orderInfo.ClosePrice;
        this.sl = orderInfo.StopLoss;
        this.tp = orderInfo.TakeProfit;
        this.profit = orderInfo.Profit;
        this.commission = orderInfo.Commission;
        this.swap = orderInfo.Swap;
        this.symbol = orderInfo.Symbol;
        this.comment = orderInfo.Comment;
    }
}
