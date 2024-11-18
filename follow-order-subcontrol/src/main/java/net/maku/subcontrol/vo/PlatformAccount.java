package net.maku.subcontrol.vo;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class PlatformAccount {
    private String key;
    private String group;
    private String type;
    private String typeString;
    private String platformType;
    private String comment;
    private int id;
    private String platformName;
    private int user;
    private BigDecimal balance;
    private BigDecimal equity;
    private BigDecimal credit;
    private BigDecimal profit;
    private double profitPercentage;
    private BigDecimal freeMargin;
    private BigDecimal margin;
    private double marginLevel;
    private int leverage;
    private int count;
    private double lots;
    private double buy;
    private double sell;
    private String modeString;
    private String placedTypeString;
    private String server;
    private boolean defaultServer;
    private String managerStatus;
    private boolean status;
    private String password;
    private int platformId;
    private List<Order> orders;

    // Getters and Setters

    public static class Order {
        private int id;
        private int login;
        private int ticket;
        private LocalDateTime openTime;
        private LocalDateTime closeTime;
        private String type;
        private double lots;
        private String symbol;
        private double openPrice;
        private double stopLoss;
        private double takeProfit;
        private double closePrice;
        private int magicNumber;
        private double swap;
        private double commission;
        private String comment;
        private String placeType;
        private BigDecimal profit;

        // Getters and Setters
    }
}
