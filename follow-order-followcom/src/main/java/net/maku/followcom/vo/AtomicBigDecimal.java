package net.maku.followcom.vo;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Author:  zsd
 * Date:  2025/3/3/周一 17:03
 */
public class AtomicBigDecimal {
    private AtomicReference<BigDecimal> atomicBigDecimal;

    public AtomicBigDecimal(BigDecimal initialValue) {
        this.atomicBigDecimal = new AtomicReference<>(initialValue);
    }

    public void add(BigDecimal value) {
        atomicBigDecimal.updateAndGet(current -> current.add(value));
    }

    public void subtract(BigDecimal value) {
        atomicBigDecimal.updateAndGet(current -> current.subtract(value));
    }

    public BigDecimal getValue() {
        return atomicBigDecimal.get();
    }
}
