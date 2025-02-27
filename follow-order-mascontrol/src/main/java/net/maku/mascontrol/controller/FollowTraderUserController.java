package net.maku.mascontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.enums.TraderUserEnum;
import net.maku.followcom.query.FollowFailureDetailQuery;
import net.maku.followcom.query.FollowTestServerQuery;
import net.maku.followcom.query.FollowTraderUserQuery;
import net.maku.followcom.query.FollowUploadTraderUserQuery;
import net.maku.followcom.service.*;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import net.maku.framework.security.user.SecurityUser;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 账号初始表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Slf4j
@RestController
@RequestMapping("/mascontrol/user")
@Tag(name="账号初始表")
@AllArgsConstructor
public class FollowTraderUserController {
    private final FollowTraderUserService followTraderUserService;
    private final FollowUploadTraderUserService followUploadTraderUserService;
    private final FollowPlatformService followPlatformService;
    private final FollowTestDetailService followTestDetailService;
    private final FollowFailureDetailService followFailureDetailService;
    private final FollowTraderService followTraderService;
    private final FollowTraderSubscribeService followTraderSubscribeService;
    private final FollowVpsService followVpsService;
    private final RedisCache redisCache;

/*   @PostConstruct
    public void init() {
        List<FollowTraderEntity> list = followTraderService.list();
       List<FollowPlatformEntity> list1 = followPlatformService.list();
       Map<Long, FollowPlatformEntity> collect = list1.stream().collect(Collectors.toMap(FollowPlatformEntity::getId, Function.identity()));
       List<FollowTraderUserEntity> ls=new ArrayList<FollowTraderUserEntity>();
        list.forEach(t->{
            FollowTraderUserEntity entity =new FollowTraderUserEntity();
            entity.setId(t.getId());
            entity.setAccount(t.getAccount());
            entity.setPassword(t.getPassword());
            entity.setPlatformId(t.getPlatformId());
            entity.setPlatform(t.getPlatform());
            entity.setPassword(t.getPassword());
            entity.setAccountType("MT4");
            entity.setServerNode(collect.get(Long.parseLong(t.getPlatformId().toString())).getServerNode());
            entity.setGroupName("默认");
            entity.setGroupId(1);
            entity.setStatus(1);
            entity.setDeleted(0);
            ls.add(entity);
        });
        followTraderUserService.saveBatch(ls);
    }*/
    
    @GetMapping("page")
    @Operation(summary = "分页")
    public Result<PageResult<FollowTraderUserVO>> page(@ParameterObject @Valid FollowTraderUserQuery query){
        PageResult<FollowTraderUserVO> page = followTraderUserService.page(query);
        List<FollowTraderSubscribeEntity> subscribes = followTraderSubscribeService.list();
        List<FollowTraderEntity> traders = followTraderService.list();
        List<FollowVpsEntity> vpsList = followVpsService.list();
        List<FollowPlatformEntity> platforms = followPlatformService.list();
        Map<Long,FollowTraderSubscribeEntity> subscribeMap=new HashMap<>();
        Map<String,List<FollowTraderEntity>> traderMap=new HashMap<>();
        Map<Integer,FollowVpsEntity> vpsMap=new HashMap<>();
        subscribes.stream().forEach(s->{
            subscribeMap.put(s.getSlaveId(),s);
        });
        traders.stream().forEach(t->{
            List<FollowTraderEntity> followTraderEntities = traderMap.get(t.getAccount() + "-" + t.getPlatformId());
           if (ObjectUtil.isEmpty(followTraderEntities)) {
               followTraderEntities = new ArrayList<>();

           }
            followTraderEntities.add(t);
            traderMap.put(t.getAccount() + "-" + t.getPlatformId(), followTraderEntities);
        });
        vpsList.forEach(v->{
            vpsMap.put(v.getId(),v);
        });
        Map<Long, FollowPlatformEntity> platformMap = platforms.stream().collect(Collectors.toMap(FollowPlatformEntity::getId, Function.identity()));
        LambdaQueryWrapper<FollowTraderEntity> wrapper = new LambdaQueryWrapper<>();
        List<FollowTraderUserVO> list = page.getList();
        StringBuilder sb=new StringBuilder();
        list.forEach(o->{
            FollowPlatformEntity followPlatformEntity = platformMap.get(Long.parseLong(o.getPlatformId().toString()));
            o.setBrokerName(followPlatformEntity.getBrokerName());
            String key=o.getAccount() + "-" + o.getPlatformId();
            ArrayList<VpsDescVO> vpsDesc = new ArrayList<>();
            List<FollowTraderEntity> followTraderEntities = traderMap.get(key);
            if(o.getStatus().equals(CloseOrOpenEnum.OPEN.getValue())){
                AtomicReference<FollowRedisTraderVO> followRedisTraderVO = new AtomicReference<>();
                  followTraderEntities.forEach(f->{
                    if(f.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue())){
                            followRedisTraderVO.set((FollowRedisTraderVO) redisCache.get(Constant.TRADER_USER + f.getId()));
                    }
                    if(f.getType().equals(TraderTypeEnum.MASTER_REAL)){
                        VpsDescVO vo = VpsDescVO.builder().desc(f.getIpAddr() +"-"+ vpsMap.get(f.getServerId()).getName() + "-跟单策略").statusExtra(f.getStatusExtra()).status(f.getStatus()).build();
                        vpsDesc.add(vo);
                    }else{
                        VpsDescVO vo = VpsDescVO.builder().desc(f.getIpAddr() +"-"+ vpsMap.get(f.getServerId()).getName()+"-跟单账号").statusExtra(f.getStatusExtra()).status(f.getStatus()).build();
                        vpsDesc.add(vo);

                    }
                }) ;
                  if(ObjectUtil.isEmpty(followRedisTraderVO.get())){
                      Object o1 = redisCache.get(Constant.TRADER_USER + followTraderEntities.get(0).getId());
                      followRedisTraderVO.set((FollowRedisTraderVO) o1);
                  }
                FollowRedisTraderVO redisTraderVo = followRedisTraderVO.get();
                  if(ObjectUtil.isNotEmpty(redisTraderVo)){
                      BigDecimal euqit = redisTraderVo.getEuqit();
                      o.setEuqit(euqit);
                      BigDecimal balance = redisTraderVo.getBalance();
                      o.setBalance(balance);
                      BigDecimal marginProportion = redisTraderVo.getMarginProportion();
                      o.setMarginProportion(marginProportion);
                      o.setFreeMargin(redisTraderVo.getFreeMargin());
                      o.setMargin(redisTraderVo.getMargin());
                      o.setTotal(redisTraderVo.getTotal());
                      o.setSellNum(redisTraderVo.getSellNum());
                      o.setBuyNum(redisTraderVo.getBuyNum());


                  }
                o.setVpsDesc(vpsDesc);
           }else{
                if(ObjectUtil.isEmpty(followTraderEntities)) {
                    VpsDescVO vo = VpsDescVO.builder().desc("-交易分配").build();
                    vpsDesc.add(vo);
                }else{
                    followTraderEntities.forEach(f->{
                        VpsDescVO vo = VpsDescVO.builder().desc(f.getIpAddr() +"-"+ vpsMap.get(f.getServerId()).getName()+"-交易分配").statusExtra(f.getStatusExtra()).status(f.getStatus()).build();
                        vpsDesc.add(vo);
                    });
                }

               o.setVpsDesc(vpsDesc);
           }

        });

        return Result.ok(page);
    }
    @GetMapping("getStatInfo")
    @Operation(summary = "信息")
    public Result<TraderUserStatVO> getStatInfo(){
        TraderUserStatVO data = followTraderUserService.getStatInfo();

        return Result.ok(data);
    }

    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public Result<FollowTraderUserVO> get(@PathVariable("id") Long id){
        FollowTraderUserVO data = followTraderUserService.get(id);

        return Result.ok(data);
    }

    @PostMapping
    @Operation(summary = "保存")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public Result<String> save(@RequestBody FollowTraderUserVO vo){
        followTraderUserService.save(vo);

        return Result.ok();
    }

    @PutMapping
    @Operation(summary = "修改")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public Result<String> update(@RequestBody @Valid FollowTraderUserVO vo){
        followTraderUserService.update(vo);

        return Result.ok();
    }

    @DeleteMapping
    @Operation(summary = "删除")
    @OperateLog(type = OperateTypeEnum.DELETE)
    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public Result<String> delete(@RequestBody List<Long> idList){
        followTraderUserService.delete(idList);

        return Result.ok();
    }

    @GetMapping("export")
    @Operation(summary = "导出")
    @OperateLog(type = OperateTypeEnum.EXPORT)
    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public void export() {
        followTraderUserService.export();
    }

    @GetMapping("download")
    @Operation(summary = "下载模板")
    @OperateLog(type = OperateTypeEnum.EXPORT)
//    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public ResponseEntity<byte[]> generateCsv() {
        try {
            // 使用 ByteArrayOutputStream 来生成 CSV 数据
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            followTraderUserService.generateCsv(outputStream);

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=export.csv");
            headers.add("Content-Type", "text/csv");

            return new ResponseEntity<>(outputStream.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("导出CSV时出错".getBytes());
        }
    }

    @GetMapping("uploadPage")
    @Operation(summary = "批量记录")
//    @PreAuthorize("hasAuthority('mascontrol:uploadTraderUser')")
    public Result<PageResult<FollowUploadTraderUserVO>> page(@ParameterObject @Valid FollowUploadTraderUserQuery query){
        PageResult<FollowUploadTraderUserVO> page = followUploadTraderUserService.page(query);

        return Result.ok(page);
    }

    @PostMapping("import")
    @Operation(summary = "导入")
    @OperateLog(type = OperateTypeEnum.IMPORT)
//    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<String> addExcel(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return Result.error("请选择需要上传的文件");
        }
        // 检查文件大小
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            return Result.error("上传的文件大小不能超过 10MB");
        }
        try {
            // 检查文件类型
            if (!isExcelOrCsv(file.getOriginalFilename())) {
                return Result.error("仅支持 Excel 和 CSV 文件");
            }
            //设置状态
            FollowUploadTraderUserVO followUploadTraderUserVO = new FollowUploadTraderUserVO();
            followUploadTraderUserVO.setStatus(TraderUserEnum.IN_PROGRESS.getType());
            followUploadTraderUserVO.setOperator(SecurityUser.getUser().getUsername());
            followUploadTraderUserVO.setUploadTime(LocalDateTime.now());
            followUploadTraderUserService.save(followUploadTraderUserVO);

            //查询最新的记录
            FollowUploadTraderUserEntity saved = followUploadTraderUserService.getOne(new QueryWrapper<FollowUploadTraderUserEntity>().orderByDesc("id").last("limit 1"));
            Long savedId = saved.getId();
            // 导入文件
            followTraderUserService.addByExcel(file,savedId);
            return Result.ok("新增成功");
        } catch (Exception e) {
            return Result.error("新增失败：" + e.getMessage());
        }
    }

    /**
     * 检查文件是否为 Excel 或 CSV 格式
     */
    private boolean isExcelOrCsv(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return extension.equals("xlsx") || extension.equals("xls") || extension.equals("csv");
    }

    @GetMapping("pageDetail")
    @Operation(summary = "失败详情")
//    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public Result<PageResult<FollowFailureDetailVO>> pageDetail(@ParameterObject @Valid FollowFailureDetailQuery query){
        PageResult<FollowFailureDetailVO> page = followFailureDetailService.page(query);

        return Result.ok(page);
    }

    @GetMapping("listServerOrder")
    @Operation(summary = "查询当前存在服务商列表")
    public Result<List<FollowPlatformEntity>> listServerOrder() {
        return Result.ok(followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>().orderByDesc(FollowPlatformEntity::getCreateTime)));
    }

    @GetMapping("listHavingServer")
    @Operation(summary = "查询节点列表")
    public Result<List<FollowTestDetailVO>> listHavingServer(@Parameter FollowTestServerQuery query) {
        List<FollowTestDetailVO> list = followTestDetailService.selectServerNode(query);

        return Result.ok(list);
    }

    @PutMapping("updateGroup")
    @Operation(summary = "批量修改分组")
    @OperateLog(type = OperateTypeEnum.UPDATE)
//    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public Result<String> updateGroup(@RequestBody List<Long> idList,@RequestBody String group) {
        followTraderUserService.updateGroup(idList,group);

        return Result.ok("批量修改分组成功");
    }

    @PutMapping("updatePasswords")
    @Operation(summary = "批量修改密码")
    @OperateLog(type = OperateTypeEnum.UPDATE)
//    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public Result<String> updatePasswords(@RequestBody List<FollowTraderUserVO> voList,@RequestBody String password,@RequestBody String confirmPassword, HttpServletRequest req) throws Exception {

        followTraderUserService.updatePasswords(voList,password,confirmPassword,req);

        return Result.ok("批量修改密码成功");
    }

    @PutMapping("updatePassword")
    @Operation(summary = "修改密码")
    @OperateLog(type = OperateTypeEnum.UPDATE)
//    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public Result<String> updatePassword(@RequestBody FollowTraderUserVO vo,HttpServletRequest req) throws Exception{

        followTraderUserService.updatePassword(vo,req);

        return Result.ok("修改密码成功");
    }

}