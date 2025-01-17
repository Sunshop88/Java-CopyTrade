package net.maku.mascontrol.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowTraderAnalysisEntity;
import net.maku.followcom.query.DashboardAccountQuery;
import net.maku.followcom.query.SymbolAnalysisQuery;
import net.maku.followcom.service.DashboardService;
import net.maku.followcom.vo.*;
import net.maku.framework.common.query.Query;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * Author:  zsd
 * Date:  2025/1/2/周四 9:52
 */
@RestController
@RequestMapping("/dashboard")
@Tag(name = "仪表盘")
@AllArgsConstructor
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    /***
     * 仪表盘-头部统计
     * **/
    @GetMapping("/getStatData")
    @Operation(summary = "仪表盘-头部统计")
    public Result<StatDataVO> getStatData() {

        return Result.ok(dashboardService.getStatData());
    }

    /***
     * 仪表盘-盈利排行榜
     * **/
    @GetMapping("/getRanking")
    @Operation(summary = "仪表盘-盈利排行榜")
    public Result<List<RankVO>> getRanking(@ParameterObject Query query) {

        return Result.ok(dashboardService.getRanking(query));
    }
    /**
     * 仪表盘-账号数据监控-账号数据
     * */
    @GetMapping("/getAccountDataPage")
    @Operation(summary = "账号数据")
    @PreAuthorize("hasAuthority('dashboard:accountData')")
    public Result<List<DashboardAccountDataVO>> getAccountDataPage(@ParameterObject @Valid DashboardAccountQuery vo) {

        return Result.ok(dashboardService.getAccountDataPage(vo));
    }
    /**
     * 仪表盘-头寸监控-统计
     *    @ParameterObject @Valid SymbolAnalysisQuery vo
     * */
    @GetMapping("/getSymbolAnalysis")
    @Operation(summary = "仪表盘-头寸监控-统计")
    public Result<List<SymbolChartVO>> getSymbolAnalysis() {

        return Result.ok(dashboardService.getSymbolAnalysis());
    }

/*    *//**
     * 仪表盘-头寸监控-统计明细
     *
     * *//*
    @GetMapping("/getSymbolAnalysisDetails")
    @Operation(summary = "仪表盘-头寸监控-统计明细")
    public Result<List<FollowTraderAnalysisEntity>> getSymbolAnalysisDetails(@ParameterObject @Valid TraderAnalysisVO vo ) {

        return Result.ok(dashboardService.getSymbolAnalysisDetails(vo));
    }*/

    /**
     * 仪表盘-头寸监控-统计明细
     *
     * */
    @GetMapping("/getSymbolAnalysisDetails")
    @Operation(summary = "仪表盘-头寸监控-统计明细")
    public Result<Map<String,List<FollowTraderAnalysisEntity>>> getSymbolAnalysisDetails() {

        return Result.ok(dashboardService.getSymbolAnalysisMapDetails());
    }
    /***
     * 仪表盘-Symbool数据图表
     * **/
    @GetMapping("/getSymbolChart")
    @Operation(summary = "仪表盘-Symbol数据图表")
    public Result<List<SymbolChartVO>> getSymbolChart() {

        return Result.ok(dashboardService.getSymbolChart());
    }

    /***
     * 仪表盘-劵商搜索
     * **/
    @GetMapping("/searchPlatform")
    @Operation(summary = "仪表盘-劵商搜索")
    public Result<List<FollowPlatformEntity>> searchPlatform(String brokerName) {

        return Result.ok(dashboardService.searchPlatform(brokerName));
    }

    /***
     * 仪表盘-劵商搜索
     * **/
    @GetMapping("/searchBrokerName")
    @Operation(summary = "仪表盘-劵商搜索")
    public Result<List<FollowPlatformEntity>> searchBrokerName(String brokerName) {

        return Result.ok(dashboardService.searchBrokerName(brokerName));
    }

}
