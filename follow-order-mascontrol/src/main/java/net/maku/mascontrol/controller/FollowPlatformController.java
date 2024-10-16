package net.maku.mascontrol.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import net.maku.framework.security.user.SecurityUser;
import net.maku.mascontrol.convert.FollowPlatformConvert;
import net.maku.mascontrol.entity.FollowPlatformEntity;
import net.maku.mascontrol.query.FollowPlatformQuery;
import net.maku.mascontrol.service.FollowPlatformService;
import net.maku.mascontrol.vo.FollowPlatformVO;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
* 平台管理
*
* @author
* @since 1.0.0 2024-09-11
*/
@RestController
@RequestMapping("/mascontrol/platform")
@Tag(name="平台管理")
@AllArgsConstructor
public class FollowPlatformController {
    private final FollowPlatformService followPlatformService;
    private final FollowBrokeServerService followBrokeServerService;


    @GetMapping("page")
    @Operation(summary = "分页")
    @PreAuthorize("hasAuthority('mascontrol:platform')")
    public Result<PageResult<FollowPlatformVO>> page(@ParameterObject @Valid FollowPlatformQuery query){
        PageResult<FollowPlatformVO> page = followPlatformService.page(query);

        return Result.ok(page);
    }

    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('mascontrol:platform')")
    public Result<FollowPlatformVO> get(@PathVariable("id") Long id){
        FollowPlatformEntity entity = followPlatformService.getById(id);

        return Result.ok(FollowPlatformConvert.INSTANCE.convert(entity));
    }

    @PostMapping
    @Operation(summary = "保存")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:platform')")
    public Result<String> save(@RequestBody FollowPlatformVO vo){
        //判断是否已存在服务名称
        vo.getPlatformList().forEach(bro->{
            FollowPlatformVO followPlatformVO=vo;
            followPlatformVO.setServer(bro);
            followPlatformService.save(followPlatformVO);
            //进行测速
            List<FollowBrokeServerEntity> list = followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName, bro));
            list.parallelStream().forEach(o->{
                String ipAddress = o.getServerNode(); // 目标IP地址
                int port = Integer.valueOf(o.getServerPort()); // 目标端口号
                try {
                    AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
                    long startTime = System.currentTimeMillis(); // 记录起始时间
                    Future<Void> future = socketChannel.connect(new InetSocketAddress(ipAddress, port));
                    // 等待连接完成
                    future.get();
                    long endTime = System.currentTimeMillis(); // 记录结束时间
                    o.setSpeed((int)endTime - (int)startTime);
                    followBrokeServerService.updateById(o);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            list.stream().map(FollowBrokeServerEntity::getServerName).distinct().forEach(o->{
                //找出最小延迟
                FollowBrokeServerEntity followBrokeServer = followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName, o).orderByAsc(FollowBrokeServerEntity::getSpeed)).get(0);
                //修改所有用户连接节点
                followPlatformService.update(Wrappers.<FollowPlatformEntity>lambdaUpdate().eq(FollowPlatformEntity::getServer,followBrokeServer.getServerName()).set(FollowPlatformEntity::getServerNode,followBrokeServer.getServerNode()+":"+followBrokeServer.getServerPort()));
            });
        });
        return Result.ok();
    }

    @PutMapping
    @Operation(summary = "修改")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:platform')")
    public Result<String> update(@RequestBody @Valid FollowPlatformVO vo){
        followPlatformService.update(vo);
        //保存服务数据
        ThreadPoolUtils.execute(()->{
            vo.getPlatformList().forEach(bro->{
                vo.setId(null);
                FollowPlatformVO followPlatformVO=vo;
                followPlatformVO.setServer(bro);
                followPlatformService.save(followPlatformVO);
                //进行测速
                List<FollowBrokeServerEntity> list = followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName, bro));
                list.parallelStream().forEach(o->{
                    String ipAddress = o.getServerNode(); // 目标IP地址
                    int port = Integer.valueOf(o.getServerPort()); // 目标端口号
                    try {
                        AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
                        long startTime = System.currentTimeMillis(); // 记录起始时间
                        Future<Void> future = socketChannel.connect(new InetSocketAddress(ipAddress, port));
                        // 等待连接完成
                        future.get();
                        long endTime = System.currentTimeMillis(); // 记录结束时间
                        o.setSpeed((int)endTime - (int)startTime);
                        followBrokeServerService.updateById(o);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                list.stream().map(FollowBrokeServerEntity::getServerName).distinct().forEach(o->{
                    //找出最小延迟
                    FollowBrokeServerEntity followBrokeServer = followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName, o).orderByAsc(FollowBrokeServerEntity::getSpeed)).get(0);
                    //修改所有用户连接节点
                    followPlatformService.update(Wrappers.<FollowPlatformEntity>lambdaUpdate().eq(FollowPlatformEntity::getServer,followBrokeServer.getServerName()).set(FollowPlatformEntity::getServerNode,followBrokeServer.getServerNode()+":"+followBrokeServer.getServerPort()));
                });
            });
        });
        return Result.ok();
    }



    @DeleteMapping
    @Operation(summary = "删除")
    @OperateLog(type = OperateTypeEnum.DELETE)
    @PreAuthorize("hasAuthority('mascontrol:platform')")
    public Result<String> delete(@RequestBody List<Long> idList) {
        followPlatformService.delete(idList);

        return Result.ok();
    }

    @GetMapping("export")
    @Operation(summary = "导出")
    @OperateLog(type = OperateTypeEnum.EXPORT)
    @PreAuthorize("hasAuthority('mascontrol:platform')")
    public void export() {
        followPlatformService.export();
    }



//    @GetMapping("/brokeName")
//    @Operation(summary = "查询券商名称" )
//    @PreAuthorize("hasAuthority('mascontrol:platform')")
//    //查询所有的券商名称
//    public Result<List<String>> brokeName(@RequestBody List<Long> idList){
//        List<String> list = followPlatformService.getBrokeName(idList);
//        return Result.ok(list);
//    }

    @GetMapping("list")
    @Operation(summary = "查询列表")
    public Result<List<FollowPlatformVO>> list(){
        List<FollowPlatformVO> list = followPlatformService.getList();
        return Result.ok(list);
    }

    @GetMapping("listServer")
    @Operation(summary = "查询服务商列表")
    public Result<List<FollowBrokeServerEntity>> listServer(@Parameter(description = "name") String name){
        List<FollowBrokeServerEntity> list = followBrokeServerService.listByServerNameGroup(name);
        //过滤已存在服务
        List<String> collect = followPlatformService.getList().stream().map(o -> o.getServer()).collect(Collectors.toList());
        List<FollowBrokeServerEntity> followList = list.stream().filter(o -> !collect.contains(o.getServerName())).collect(Collectors.toList());
        return Result.ok(followList);
    }


    @GetMapping("listBroke")
    @Operation(summary = "查询券商列表")
    public Result<List<FollowPlatformVO>> listBroke(){
        List<FollowPlatformVO> list = followPlatformService.listBroke();
        return Result.ok(list);
    }

    @GetMapping("listServerOrder")
    @Operation(summary = "查询当前存在服务商列表")
    public Result<List<FollowPlatformEntity>> listServerOrder(){
        return Result.ok(followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>().orderByDesc(FollowPlatformEntity::getCreateTime)));
    }
}