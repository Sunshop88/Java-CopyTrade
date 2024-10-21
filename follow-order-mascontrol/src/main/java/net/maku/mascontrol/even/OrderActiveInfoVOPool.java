package net.maku.mascontrol.even;

import net.maku.followcom.vo.OrderActiveInfoVO;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class OrderActiveInfoVOPool {
    private final Queue<OrderActiveInfoVO> pool = new ConcurrentLinkedQueue<>();

    // 获取对象
    public OrderActiveInfoVO borrowObject() {
        OrderActiveInfoVO vo = pool.poll();
        return (vo == null) ? new OrderActiveInfoVO() : vo;
    }

    // 归还对象
    public void returnObject(OrderActiveInfoVO vo) {
        resetOrderActiveInfoVO(vo);  // 重置对象
        pool.offer(vo);              // 放回池中
    }

    // 重置对象内容
    private void resetOrderActiveInfoVO(OrderActiveInfoVO vo) {
        vo.setAccount(null);
        vo.setLots(0);
        vo.setComment(null);
        vo.setOrderNo(0);
        vo.setCommission(0);
        vo.setSwap(0);
        vo.setProfit(0);
        vo.setSymbol(null);
        vo.setOpenPrice(0);
        vo.setMagicNumber(0);
        vo.setType(null);
        vo.setOpenTime(null);
        vo.setStopLoss(0);
        vo.setTakeProfit(0);
    }
}
