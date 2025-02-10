package net.maku.mascontrol.websocket;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderAnalysisEntity;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.query.DashboardAccountQuery;
import net.maku.followcom.service.DashboardService;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.rmi.ServerException;
import java.util.*;
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


    private final DashboardService dashboardService = SpringContextUtils.getBean(DashboardService.class);
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private Map<String, ScheduledFuture<?>> scheduledFutureMap = new HashMap<>();

    // 当客户端连接时调用
/*    @OnOpen
    public void onOpen(Session session, @PathParam("rankOrder") String rankOrder, @PathParam("rankAsc") Boolean rankAsc, @PathParam("brokerName") String brokerName,
                       @PathParam("accountOrder") String accountOrder, @PathParam("accountPage") Integer accountPage,@PathParam("accountAsc") Boolean accountAsc) throws IOException  {*/
    @OnOpen
    public void onOpen(Session session) throws IOException {
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

    private JSONObject send(String rankOrder, Boolean rankAsc, String brokerName,
                            String accountOrder, Integer accountPage, Boolean accountAsc, Integer accountLimit, String server, String vpsName, String account, String sourceAccount, TraderAnalysisVO analysisVO) {
        //仪表盘-头部统计
        StatDataVO statData = dashboardService.getStatData();
        //仪表盘-头寸监控-统计
        //  List<SymbolChartVO> symbolAnalysis = dashboardService.getSymbolAnalysis();
        Map<String, SymbolChartVO> symbolAnalysisMap = new HashMap<>();
        //List<SymbolChartVO> symbolAnalysis=new ArrayList<>();
        //仪表盘-头寸监控-统计明细
        List<FollowTraderAnalysisEntity> details = dashboardService.getSymbolAnalysisDetails(analysisVO);
        // Map<String, List<FollowTraderAnalysisEntity>> symbolAnalysisMapDetails = dashboardService.getSymbolAnalysisMapDetails();
        //仪表盘-Symbol数据图表 和 仪表盘-头寸监控-统计
        List<SymbolChartVO> symbolChart = new ArrayList<>();
        // List<SymbolChartVO> symbolChart = dashboardService.getSymbolChart();
        //仪表盘-盈利排行榜
        List<RankVO> ranking = new ArrayList<>();
       /* if (ObjectUtil.isNotEmpty(rankOrder)) {
            Query query = new Query();
            query.setAsc(rankAsc);
            query.setOrder(rankOrder);
            query.setLimit(10);
            ranking = dashboardService.getRanking(query);
        }*/

        //账号数据
        List<DashboardAccountDataVO> accountDataPage = null;
        if (ObjectUtil.isNotEmpty(accountPage)) {
            DashboardAccountQuery vo = new DashboardAccountQuery();
            if (accountLimit == null) {
                vo.setLimit(50);
            } else {
                vo.setLimit(accountLimit);
            }
            vo.setAccount(account);
            vo.setPage(accountPage);
            vo.setAsc(accountAsc);
            if (ObjectUtil.isNotEmpty(brokerName)) {
                List<String> brokers = JSONArray.parseArray(brokerName, String.class);
                String brokerstr = String.join(",", brokers);
                vo.setBrokerName(brokerstr);
            }
            if (ObjectUtil.isNotEmpty(server)) {
                List<String> servers = JSONArray.parseArray(server, String.class);
                String serversstr = String.join(",", servers);
                vo.setServer(serversstr);
            }
            vo.setOrder(accountOrder);
            vo.setAccount(account);
            vo.setSourceAccount(sourceAccount);
            accountDataPage = dashboardService.getAccountDataPage(vo);
            //遍历账号数据修改仪表盘统计数据
            Map<String, Integer> accountMap = new HashMap<>();
            //总订单数
            statData.setNum(BigDecimal.ZERO);
            //持仓手数
            statData.setLots(BigDecimal.ZERO);
            //持仓账号数量
            statData.setFollowActiveNum(0);
            statData.setVpsActiveNum(0);
            statData.setSourceActiveNum(0);
            statData.setProfit(BigDecimal.ZERO);
            Map<String, Integer> followMapNum = new HashMap<>();
            if (ObjectUtil.isNotEmpty(accountDataPage)) {
                List<RankVO> rank = new ArrayList<>();
                accountDataPage.forEach(a -> {
                    Integer i = accountMap.get(a.getAccount() + "_" + a.getServer());
                    if (i == null) {
                        //总订单数
                        statData.setNum(a.getOrderNum() != null ? statData.getNum().add(BigDecimal.valueOf(a.getOrderNum())) : statData.getNum());
                        //持仓手数
                        statData.setLots(a.getLots() != null ? statData.getLots().add(BigDecimal.valueOf(a.getLots())) : statData.getLots());
                        //持仓账号数量
                        followMapNum.put(a.getAccount() + a.getServer(), 0);
                        statData.setFollowActiveNum(followMapNum.keySet().size());
                        statData.setProfit(a.getProfit() != null ? statData.getProfit().add(a.getProfit()) : statData.getProfit());
                        accountMap.put(a.getAccount() + "_" + a.getServer(), 1);
                        //排行榜
                        RankVO rankVO = new RankVO();
                        rankVO.setAccount(a.getAccount());
                        rankVO.setPlatform(a.getServer());
                        rankVO.setLots(BigDecimal.valueOf(a.getLots() != null ? a.getLots() : 0));
                        rankVO.setNum(BigDecimal.valueOf(a.getOrderNum() != null ? a.getOrderNum() : 0));
                        rankVO.setProfit(a.getProfit());
                        rankVO.setFreeMargin(a.getMarginProportion());
                        rankVO.setProportion(a.getProportion());
                        rank.add(rankVO);
                    }
                });
                if (ObjectUtil.isNotEmpty(rankOrder)) {
                    ranking = getRank(rankOrder, rankAsc, rank);
                }
            }


        }
        JSONObject json = new JSONObject();
        if (ObjectUtil.isNotEmpty(analysisVO.getOrder())) {
            Map<String, Integer> map = new HashMap<>();
            Map<Long, Integer> vpsMapNum = new HashMap<>();
            Map<String, Integer> sourceMapNum = new HashMap<>();
            Map<String, Integer> followMapNum = new HashMap<>();
            statData.setNum(BigDecimal.ZERO);
            statData.setLots(BigDecimal.ZERO);
            statData.setFollowActiveNum(0);
            statData.setProfit(BigDecimal.ZERO);
            statData.setSourceActiveNum(0);
            statData.setVpsActiveNum(0);

            details.forEach(o -> {
                SymbolChartVO chartVO = symbolAnalysisMap.get(o.getSymbol());
                if (chartVO == null) {
                    chartVO = new SymbolChartVO();
                    chartVO.setSymbol(o.getSymbol());

                }
                List<FollowTraderAnalysisEntity> symbolAnalysisDetails = chartVO.getSymbolAnalysisDetails();
                if (symbolAnalysisDetails == null) {
                    symbolAnalysisDetails = new ArrayList<>();
                }
                vpsMapNum.put(o.getVpsId(), 1);
                statData.setVpsActiveNum(vpsMapNum.keySet().size());
                Integer i = map.get(o.getSymbol() + "_" + o.getAccount() + "_" + o.getPlatformId());
                if (i == null) {
                    BigDecimal lots = chartVO.getLots() == null ? BigDecimal.ZERO : chartVO.getLots();
                    BigDecimal position = chartVO.getPosition() == null ? BigDecimal.ZERO : chartVO.getPosition();
                    BigDecimal profit = chartVO.getProfit() == null ? BigDecimal.ZERO : chartVO.getProfit();
                    BigDecimal num = chartVO.getNum() == null ? BigDecimal.ZERO : chartVO.getNum();
                    BigDecimal buyNum = chartVO.getBuyNum() == null ? BigDecimal.ZERO : chartVO.getBuyNum();
                    BigDecimal buyLots = chartVO.getBuyLots() == null ? BigDecimal.ZERO : chartVO.getBuyLots();
                    BigDecimal buyProfit = chartVO.getBuyProfit() == null ? BigDecimal.ZERO : chartVO.getBuyProfit();
                    BigDecimal sellNum = chartVO.getSellNum() == null ? BigDecimal.ZERO : chartVO.getSellNum();
                    BigDecimal sellLots = chartVO.getSellLots() == null ? BigDecimal.ZERO : chartVO.getSellLots();
                    BigDecimal sellProfit = chartVO.getSellProfit() == null ? BigDecimal.ZERO : chartVO.getSellProfit();
                    lots = o.getLots() == null ? lots : o.getLots().add(lots);
                    profit = o.getProfit() == null ? profit : profit.add(o.getProfit());
                    num = o.getNum() == null ? num : num.add(o.getNum());
                    buyNum = o.getBuyNum() == null ? buyNum : buyNum.add(o.getBuyNum());
                    buyLots = o.getBuyLots() == null ? buyLots : buyLots.add(o.getBuyLots());
                    buyProfit = o.getBuyProfit() == null ? buyProfit : buyProfit.add(o.getBuyProfit());
                    sellNum = o.getSellNum() == null ? sellNum : sellNum.add(o.getSellNum());
                    sellLots = o.getSellLots() == null ? sellLots : sellLots.add(o.getSellLots());
                    sellProfit = o.getSellProfit() == null ? sellProfit : sellProfit.add(o.getSellProfit());
                    position = o.getPosition() == null ? position : position.add(o.getPosition());
                    chartVO.setLots(lots);
                    chartVO.setProfit(profit);
                    chartVO.setNum(num);
                    chartVO.setBuyNum(buyNum);
                    chartVO.setBuyLots(buyLots);
                    chartVO.setBuyProfit(buyProfit);
                    chartVO.setSellNum(sellNum);
                    chartVO.setSellLots(sellLots);
                    chartVO.setSellProfit(sellProfit);
                    chartVO.setPosition(position);
                    map.put(o.getSymbol() + "_" + o.getAccount() + "_" + o.getPlatformId(), 1);
                    //总订单数
                    statData.setNum(o.getNum() != null ? statData.getNum().add(o.getNum()) : statData.getNum());
                    //持仓手数
                    statData.setLots(o.getLots() != null ? statData.getLots().add(o.getLots()) : statData.getLots());
                    statData.setProfit(o.getProfit() != null ? statData.getProfit().add(o.getProfit()) : statData.getProfit());
                    //持仓账号数量
                    if (o.getType() == TraderTypeEnum.MASTER_REAL.getType()) {
                        sourceMapNum.put(o.getAccount() + o.getPlatform(), 1);
                        statData.setSourceActiveNum(sourceMapNum.keySet().size());
                    } else {
                        followMapNum.put(o.getAccount() + o.getPlatform(), 0);
                        statData.setFollowActiveNum(followMapNum.keySet().size());
                    }


                }
                symbolAnalysisDetails.add(o);
                chartVO.setSymbolAnalysisDetails(symbolAnalysisDetails);
                symbolAnalysisMap.put(o.getSymbol(), chartVO);
            });
            symbolAnalysisMap.values().forEach(o -> {
                SymbolChartVO symbolChartVO = new SymbolChartVO();
                BeanUtil.copyProperties(o, symbolChartVO);
                symbolChartVO.setSymbolAnalysisDetails(null);
                symbolChart.add(symbolChartVO);
            });
        }

        //仪表盘-头部统计
        json.put("statData", statData);
         /*   symbolAnalysis.forEach(o->{
                List<FollowTraderAnalysisEntity> followTraderAnalysisEntities = symbolAnalysisMapDetails.get(o.getSymbol());
                List<FollowTraderAnalysisEntity> sourceSymbolDetails=new ArrayList<>();
               Map<String, List<FollowTraderAnalysisEntityVO>>  symbolMap=new HashMap<>();
                followTraderAnalysisEntities.forEach(s->{
                    List<FollowTraderAnalysisEntityVO> ls = symbolMap.get(s.getSourceAccount()+s.getVpsId());
                    if(ls==null){
                         ls= new ArrayList<>();
                    }

                    FollowTraderAnalysisEntityVO followTraderAnalysisEntityVO = FollowTraderAnalysisConvert.INSTANCE.convertVo(s);
                    ls.add(followTraderAnalysisEntityVO);
                    symbolMap.put(s.getSourceAccount()+s.getVpsId(),ls);
                    if (s.getType().equals(TraderTypeEnum.MASTER_REAL.getType())){
                        s.setSymbolAnalysisDetails(symbolMap.get(s.getSourceAccount()+s.getVpsId()));
                        sourceSymbolDetails.add(s);

                    }
                });

                o.setSymbolAnalysisDetails(sourceSymbolDetails);
               // o.setSourceSymbolDetails(sourceSymbolDetails);
              //  o.setSymbolMap(symbolMap);
            });*/
        Collection<SymbolChartVO> symbolAnalysis = symbolAnalysisMap.values();
        //仪表盘-头寸监控-统计
        if (ObjectUtil.isNotEmpty(analysisVO.getOrder())) {
            if (analysisVO.getOrder().equals("position")) {
                if (analysisVO.getAsc()) {
                    symbolAnalysis = symbolAnalysis.stream().sorted((o1, o2) -> {
                        return o1.getPosition().compareTo(o2.getPosition());
                    }).toList();
                } else {
                    symbolAnalysis = symbolAnalysis.stream().sorted((o1, o2) -> {
                        return o2.getPosition().compareTo(o1.getPosition());
                    }).toList();
                }
            }
            if (analysisVO.getOrder().equals("lots")) {
                if (analysisVO.getAsc()) {
                    symbolAnalysis = symbolAnalysis.stream().sorted((o1, o2) -> {
                        return o1.getLots().compareTo(o2.getLots());
                    }).toList();
                } else {
                    symbolAnalysis = symbolAnalysis.stream().sorted((o1, o2) -> {
                        return o2.getLots().compareTo(o1.getLots());
                    }).toList();
                }
            }
            if (analysisVO.getOrder().equals("num")) {
                if (analysisVO.getAsc()) {
                    symbolAnalysis = symbolAnalysis.stream().sorted((o1, o2) -> {
                        return o1.getNum().compareTo(o2.getNum());
                    }).toList();
                } else {
                    symbolAnalysis = symbolAnalysis.stream().sorted((o1, o2) -> {
                        return o2.getNum().compareTo(o1.getNum());
                    }).toList();
                }
            }
            if (analysisVO.getOrder().equals("profit")) {
                if (analysisVO.getAsc()) {
                    symbolAnalysis = symbolAnalysis.stream().sorted((o1, o2) -> {
                        return o1.getProfit().compareTo(o2.getProfit());
                    }).toList();
                } else {
                    symbolAnalysis = symbolAnalysis.stream().sorted((o1, o2) -> {
                        return o2.getProfit().compareTo(o1.getProfit());
                    }).toList();
                }
            }

        }

        json.put("symbolAnalysis", symbolAnalysis);
        //仪表盘-头寸监控-统计明细
        //  json.put("symbolAnalysisMapDetails",symbolAnalysisMapDetails);
        //仪表盘-Symbol数据图表 和 仪表盘-头寸监控-统计
        json.put("symbolChart", symbolChart);
        ///仪表盘-盈利排行榜
        json.put("ranking", ranking);
        //账号数据
        json.put("accountDataPage", accountDataPage);
        return json;

    }

    private static List<RankVO> getRank(String rankOrder, Boolean rankAsc, List<RankVO> ranking) {
        Integer limit = 10;
        List<RankVO> list = new ArrayList<>();
        if (rankOrder.equals("profit")) {
            list = ranking.stream().sorted((o1, o2) -> {
                if (rankAsc) {
                    return o1.getProfit().compareTo(o2.getProfit());
                } else {
                    return o2.getProfit().compareTo(o1.getProfit());
                }

            }).limit(limit).toList();
        }
        if (rankOrder.equals("lots")) {
            list = ranking.stream().sorted((o1, o2) -> {
                if (rankAsc) {
                    return o1.getLots().compareTo(o2.getLots());
                } else {
                    return o2.getLots().compareTo(o1.getLots());
                }

            }).limit(limit).toList();
        }
        if (rankOrder.equals("num")) {
            list = ranking.stream().sorted((o1, o2) -> {
                if (rankAsc) {
                    return o1.getNum().compareTo(o2.getNum());
                } else {
                    return o2.getNum().compareTo(o1.getNum());
                }
            }).limit(limit).toList();
        }
        if (rankOrder.equals("freeMargin")) {
            list = ranking.stream().sorted((o1, o2) -> {
                if (rankAsc) {
                    return o1.getFreeMargin().compareTo(o2.getFreeMargin());
                } else {
                    return o2.getFreeMargin().compareTo(o1.getFreeMargin());
                }
            }).limit(limit).toList();
        }
        if (rankOrder.equals("proportion")) {
            list = ranking.stream().sorted((o1, o2) -> {
                if (rankAsc) {
                    return o1.getProportion().compareTo(o2.getProportion());
                } else {
                    return o2.getProportion().compareTo(o1.getProportion());
                }
            }).limit(limit).toList();
        }
        return list;
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
            //账号监控数据
            String accountOrder = jsonObject.getString("accountOrder");
            Integer accountPage = jsonObject.getInteger("accountPage");
            Integer accountLimit = jsonObject.getInteger("accountLimit");
            Boolean accountAsc = jsonObject.getBoolean("accountAsc");
            String server = jsonObject.getString("server");
            String vpsName = jsonObject.getString("vpsName");
            String account = jsonObject.getString("account");
            String sourceAccount = jsonObject.getString("sourceAccount");
            //头寸监控
            //币种
            String tsymbol = jsonObject.getString("tsymbol");
            String taccount = jsonObject.getString("taccount");
            String tvpsId = jsonObject.getString("tvpsId");
            String tplatform = jsonObject.getString("tplatform");
            String tsourceAccount = jsonObject.getString("tsourceAccount");
            String tsourcePlatform = jsonObject.getString("tsourcePlatform");
            Integer ttype = jsonObject.getInteger("ttype");
            String torder = jsonObject.getString("torder");
            Boolean tasc = jsonObject.getBoolean("tasc");
            TraderAnalysisVO vo = new TraderAnalysisVO();
            vo.setSymbol(tsymbol);
            vo.setAccount(taccount);
            vo.setVpsId(tvpsId);
            vo.setPlatform(tplatform);
            vo.setSourceAccount(tsourceAccount);
            vo.setSourcePlatform(tsourcePlatform);
            vo.setType(ttype);
            vo.setOrder(torder);
            vo.setAsc(tasc);
            ScheduledFuture<?> st = scheduledFutureMap.get(id);
            if (st != null) {
                st.cancel(true);
            }
            ScheduledFuture scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(() -> {
                try {
                    JSONObject json = send(rankOrder, rankAsc, brokerName, accountOrder, accountPage, accountAsc, accountLimit, server, vpsName, account, sourceAccount, vo);
                    session.getBasicRemote().sendText(json.toJSONString());
                } catch (Exception e) {
                    e.printStackTrace();
                    log.error("推送异常:{}", e.getMessage());

                }
            }, 0, 1, TimeUnit.SECONDS);
            scheduledFutureMap.put(id, scheduledFuture);
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
            if (session != null && session.getBasicRemote() != null) {
                session.close();
            }

        } catch (IOException e) {
            log.error("关闭链接异常{}", e.getMessage());
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
