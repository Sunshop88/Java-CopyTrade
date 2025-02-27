package com.flink.enums;

import java.util.HashMap;

/**
 * Author:  zsd
 * Date:  2025/1/17/周五 9:57
 */
public enum Op {
    Buy(0),
    Sell(1),
    BuyLimit(2),
    SellLimit(3),
    BuyStop(4),
    SellStop(5),
    Balance(6),
    Credit(7);

    public static final int SIZE = 32;
    private int intValue;
    private static HashMap<Integer, Op> mappings;

    private static HashMap<Integer,Op> getMappings() {
        if (mappings == null) {
            Class var0 = Op.class;
            synchronized(Op.class) {
                if (mappings == null) {
                    mappings = new HashMap();
                }
            }
        }

        return mappings;
    }

    private Op(int value) {
        this.intValue = value;
        getMappings().put(value, this);
    }

    public int getValue() {
        return this.intValue;
    }

    public static Op forValue(int value) {
        return getMappings().get(value);
    }
}
