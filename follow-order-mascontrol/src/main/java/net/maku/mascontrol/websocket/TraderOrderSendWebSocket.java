package net.maku.mascontrol.websocket;

import cn.hutool.core.util.ObjectUtil;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import net.maku.mascontrol.even.OnQuoteHandler;
import net.maku.mascontrol.trader.LeaderApiTrader;
import net.maku.mascontrol.trader.LeaderApiTradersAdmin;
import net.maku.mascontrol.util.SpringContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/socket/trader/orderSend/{traderId}/{symbol}") //此注解相当于设置访问URL
public class TraderOrderSendWebSocket {

    private static final Logger log = LoggerFactory.getLogger(TraderOrderSendWebSocket.class);
    private Session session;

    private String traderId;

    private String symbol;

    private OnQuoteHandler onQuoteHandler;
    /**
     * marketAccountId->SessionSet
     */
    private static Map<String, Set<Session>> sessionPool = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam(value = "traderId") String traderId, @PathParam(value = "symbol") String symbol) {
        try {
            this.session = session;
            this.traderId = traderId;
            this.symbol = symbol;
            Set<Session> sessionSet;
            if (sessionPool.containsKey(traderId+symbol)) {
                sessionSet = sessionPool.get(traderId+symbol);
            } else {
                sessionSet = new HashSet<>();
            }
            sessionSet.add(session);
            sessionPool.put(traderId+symbol, sessionSet);
            LeaderApiTrader leaderApiTrader = SpringContextUtils.getBean(LeaderApiTradersAdmin.class).getLeader4ApiTraderConcurrentHashMap().get(traderId);
            log.info("订阅该品种{}",symbol);
            leaderApiTrader.quoteClient.Subscribe(symbol);
            leaderApiTrader.quoteClient.OnQuote.addListener(new OnQuoteHandler(leaderApiTrader));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnClose
    public void onClose() {
        try {
            sessionPool.get(traderId+symbol).remove(session);
            LeaderApiTrader leaderApiTrader = SpringContextUtils.getBean(LeaderApiTradersAdmin.class).getLeader4ApiTraderConcurrentHashMap().get(traderId);
            log.info("取消订阅该品种{}",symbol);
            leaderApiTrader.quoteClient.Unsubscribe(symbol);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 服务器端推送消息
     */
    public void pushMessage(String traderId,String symbol, String message) {
        try {
            Set<Session> sessionSet = sessionPool.get(traderId+symbol);
            if (ObjectUtil.isEmpty(sessionSet))return;
            for (Session session : sessionSet) {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.getBasicRemote().sendText(message);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnMessage
    public void onMessage(String message) {
    }

    public Boolean isConnection(String traderId,String symbol) {
        return sessionPool.containsKey(traderId+symbol);
    }
}
