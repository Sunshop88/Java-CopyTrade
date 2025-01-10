package net.maku.mascontrol.websocket;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderAnalysisEntity;
import net.maku.followcom.query.DashboardAccountQuery;
import net.maku.followcom.service.DashboardService;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.DashboardAccountDataVO;
import net.maku.followcom.vo.RankVO;
import net.maku.followcom.vo.StatDataVO;
import net.maku.followcom.vo.SymbolChartVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.query.Query;
import net.maku.framework.common.utils.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.rmi.ServerException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Author:  zsd
 * Date:  2025/1/8/周三 9:33
 * /{rankOrder}/{rankAsc}/{brokerName}/{accountOrder}/{accountPage}/{accountAsc}"
 */
@Component
@ServerEndpoint("/socket/dashboardSymbol")
@Slf4j
public class WebDashboardSymbolSocket {


    private final DashboardService dashboardService= SpringContextUtils.getBean(DashboardService.class);
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private Map<String,ScheduledFuture<?>> scheduledFutureMap = new HashMap<>();
    // 当客户端连接时调用
/*    @OnOpen
    public void onOpen(Session session, @PathParam("rankOrder") String rankOrder, @PathParam("rankAsc") Boolean rankAsc, @PathParam("brokerName") String brokerName,
                       @PathParam("accountOrder") String accountOrder, @PathParam("accountPage") Integer accountPage,@PathParam("accountAsc") Boolean accountAsc) throws IOException  {*/
        @OnOpen
        public void onOpen(Session session) throws IOException  {
    /*      String id = session.getId();
      //开启定时任务
        ScheduledFuture    scheduledFuture= scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                JSONObject json = send(rankOrder, rankAsc, brokerName, accountOrder, accountPage, accountAsc);
                session.getBasicRemote().sendText(json.toJSONString());
            } catch (Exception e) {
                log.error("推送异常:{}",e.getMessage());

            }
        }, 0, 1, TimeUnit.SECONDS);
        this.scheduledFutureMap.put(id,scheduledFuture);*/
    }

        private JSONObject send(String rankOrder,  Boolean rankAsc,  String brokerName,
                                 String accountOrder,  Integer accountPage, Boolean accountAsc){
            //仪表盘-头部统计
            StatDataVO statData = dashboardService.getStatData();
            //仪表盘-头寸监控-统计
            List<SymbolChartVO> symbolAnalysis = dashboardService.getSymbolAnalysis();
            //仪表盘-头寸监控-统计明细
            Map<String, List<FollowTraderAnalysisEntity>> symbolAnalysisMapDetails = dashboardService.getSymbolAnalysisMapDetails();
            //仪表盘-Symbol数据图表 和 仪表盘-头寸监控-统计
            List<SymbolChartVO> symbolChart = dashboardService.getSymbolChart();
            //仪表盘-盈利排行榜
            Query query=new Query();
            query.setAsc(rankAsc);
            query.setOrder(rankOrder);
            query.setLimit(10);
            List<RankVO> ranking = dashboardService.getRanking(query);
            //账号数据
            DashboardAccountQuery vo=new DashboardAccountQuery();
            vo.setLimit(20);
            vo.setPage(accountPage);
            vo.setAsc(accountAsc);

            if(!brokerName.equals("null")){
                vo.setBrokerName(brokerName);
            }
            vo.setBrokerName(null);
            PageResult<DashboardAccountDataVO> accountDataPage = dashboardService.getAccountDataPage(vo);
            JSONObject json=new JSONObject();
            //仪表盘-头部统计
            json.put("statData",statData);
            //仪表盘-头寸监控-统计
            json.put("symbolAnalysis",symbolAnalysis);
            //仪表盘-头寸监控-统计明细
            json.put("symbolAnalysisMapDetails",symbolAnalysisMapDetails);
            //仪表盘-Symbol数据图表 和 仪表盘-头寸监控-统计
            json.put("symbolChart",symbolChart);
            ///仪表盘-盈利排行榜
            json.put("ranking",ranking);
            //账号数据
            json.put("accountDataPage",accountDataPage);
            return  json;

    }

    // 当接收到客户端的消息时调用
    @OnMessage
    public void onMessage(String message, Session session) throws ServerException {
        try {
            String id = session.getId();
            JSONObject jsonObject = JSONObject.parseObject(message);
            String rankOrder = jsonObject.getString("rankOrder");
            Boolean rankAsc = jsonObject.getBoolean("rankAsc");
            String brokerName = jsonObject.getString("brokerName");
            String accountOrder = jsonObject.getString("accountOrder");
            Integer accountPage = jsonObject.getInteger("accountPage");
            Boolean accountAsc = jsonObject.getBoolean("accountAsc");

            String server = jsonObject.getString("server");
            String vpsName = jsonObject.getString("vpsName");
            String account = jsonObject.getString("account");
            String sourceAccount = jsonObject.getString("sourceAccount");
            ScheduledFuture<?> st = scheduledFutureMap.get(id);
            if(st!=null){
                st.cancel(true);
            }
            ScheduledFuture    scheduledFuture= scheduledExecutorService.scheduleAtFixedRate(() -> {
                try {
                    JSONObject json = send(rankOrder, rankAsc, brokerName, accountOrder, accountPage, accountAsc);
                    session.getBasicRemote().sendText(json.toJSONString());
                } catch (Exception e) {
                    log.error("推送异常:{}",e.getMessage());

                }
            }, 0, 1, TimeUnit.SECONDS);
            scheduledFutureMap.put(id,scheduledFuture);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new ServerException(e.getMessage());

        }
    }

    // 当客户端断开连接时调用
    @OnClose
    public void onClose(Session session) {
        try {
            String id = session.getId();
            ScheduledFuture<?> scheduledFuture = scheduledFutureMap.get(id);
            if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
                scheduledFuture.cancel(true);
            }
            if(session!=null && session.getBasicRemote()!=null) {
                session.close();
            }

        } catch (IOException e) {
            log.error("关闭链接异常{}",e.getMessage());
            throw new RuntimeException(e);
        }

    }

    // 当发生错误时调用
    @OnError
    public void onError(Session session, Throwable throwable) {
        System.out.println("Error occurred: " + throwable.getMessage());
        throwable.printStackTrace();
    }

}
