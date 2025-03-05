package net.maku.followcom.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.convert.FollowTraderUserConvert;
import net.maku.followcom.dao.FollowTraderUserDao;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.enums.TraderUserEnum;
import net.maku.followcom.enums.TraderUserTypeEnum;
import net.maku.followcom.query.FollowTraderUserQuery;
import net.maku.followcom.service.*;
import net.maku.followcom.util.AesUtils;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.security.user.SecurityUser;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 账号初始表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowTraderUserServiceImpl extends BaseServiceImpl<FollowTraderUserDao, FollowTraderUserEntity> implements FollowTraderUserService {
    private final TransService transService;
    private final FollowPlatformService followPlatformService;
    private final FollowGroupService followGroupService;
    private final FollowTraderService followTraderService;
    private final FollowUploadTraderUserService followUploadTraderUserService;
    private final FollowFailureDetailService followFailureDetailService;
    private final FollowTraderSubscribeService followTraderSubscribeService;
    private final FollowVpsService followVpsService;
    private final RedisCache redisCache;

    @Override
    public PageResult<FollowTraderUserVO> page(FollowTraderUserQuery query) {
        IPage<FollowTraderUserEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowTraderUserConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowTraderUserEntity> getWrapper(FollowTraderUserQuery query){
        LambdaQueryWrapper<FollowTraderUserEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(FollowTraderUserEntity::getDeleted,CloseOrOpenEnum.CLOSE.getValue());
        //备注
        if (ObjectUtil.isNotEmpty(query.getRemark())){
            wrapper.like(FollowTraderUserEntity::getRemark,query.getRemark());
        }
        //挂号的vpsId和连接状态和账号类型
        if (ObjectUtil.isNotEmpty(query.getVpsIds()) || ObjectUtil.isNotEmpty(query.getAccountType()) || ObjectUtil.isNotEmpty(query.getStatus())){
            LambdaQueryWrapper<FollowTraderEntity> wp = new LambdaQueryWrapper<>();
            wp.in(ObjectUtil.isNotEmpty(query.getVpsIds()),FollowTraderEntity::getServerId, query.getVpsIds());
            if (ObjectUtil.isNotEmpty(query.getAccountType())){
                wp.in(FollowTraderEntity::getType,query.getAccountType());
            }
            if(ObjectUtil.isNotEmpty(query.getStatus())){
               List<String> statusExtra=new ArrayList<>();

                if(query.getStatus().contains(CloseOrOpenEnum.OPEN.getValue())){
                    statusExtra.add("账户密码错误");

                }
                 if(query.getStatus().contains(CloseOrOpenEnum.CLOSE.getValue()) ){
                     statusExtra.add("账号在线");
                     statusExtra.add("启动成功");
                 }
                if(query.getStatus().contains(2) ){
                    statusExtra.add("账号异常");
                    statusExtra.add("账号掉线");
                }
                wp.in(FollowTraderEntity::getStatusExtra,statusExtra);
            }
            List<FollowTraderEntity> list = followTraderService.list(wp);
            if(ObjectUtil.isNotEmpty(list)){
                //再通过list
                StringBuilder sb = new StringBuilder();
                list.forEach(o->{
                    sb.append("'"+o.getAccount()+"-"+o.getPlatformId()+"',");
                });
                String sql =sb.substring(0, sb.length() - 1);
                wrapper.apply("concat(account,'-',platform_id) in (" +sql+")");
            }else{
                wrapper.eq(FollowTraderUserEntity::getDeleted,2);
            }

        }
        //组别
        if(ObjectUtil.isNotEmpty(query.getGroupIds()) ){
            wrapper.in(FollowTraderUserEntity::getGroupId,query.getGroupIds());
        }
        //劵商名称
        if(ObjectUtil.isNotEmpty(query.getBrokerName())){
            List<FollowPlatformEntity> list = followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>().like(FollowPlatformEntity::getBrokerName, query.getBrokerName()));
              if(ObjectUtil.isNotEmpty(list)){
                  List<Long> platformIds = list.stream().map(FollowPlatformEntity::getId).toList();
                  wrapper.in(FollowTraderUserEntity::getPlatformId,platformIds);
              }
        }
        //  服务器
        if (ObjectUtil.isNotEmpty(query.getPlatform())){
            wrapper.like(FollowTraderUserEntity::getPlatform,query.getPlatform());
        }
        if (ObjectUtil.isNotEmpty(query.getAccount())){
            wrapper.like(FollowTraderUserEntity::getAccount,query.getAccount());
        }
        if (ObjectUtil.isNotEmpty(query.getGroupName())){
            wrapper.in(FollowTraderUserEntity::getGroupName,query.getGroupName());
        }



        return wrapper;
    }


    @Override
    public FollowTraderUserVO get(Long id) {
        FollowTraderUserEntity entity = baseMapper.selectById(id);
        FollowTraderUserVO vo = FollowTraderUserConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowTraderUserVO vo) {
        List<FollowTraderUserEntity> entities = list(new LambdaQueryWrapper<FollowTraderUserEntity>().eq(FollowTraderUserEntity::getAccount, vo.getAccount()).eq(FollowTraderUserEntity::getPlatform, vo.getPlatform()));
        if (ObjectUtil.isNotEmpty(entities)){
            throw new ServerException("重复添加,请重新输入");
        }
        FollowTraderUserEntity entity = FollowTraderUserConvert.INSTANCE.convert(vo);
        FollowPlatformEntity first = followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, vo.getPlatform())).getFirst();
        if (ObjectUtil.isNotEmpty(first)){
            entity.setPlatformId(Math.toIntExact(first.getId()));
        }
        entity.setPassword(AesUtils.aesEncryptStr(entity.getPassword()));

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowTraderUserVO vo, HttpServletRequest req) {
        //只修改密码和备注信息
        FollowTraderUserEntity ent = new FollowTraderUserEntity();
        ent.setId(vo.getId());
        ent.setPassword(AesUtils.aesEncryptStr(vo.getPassword()));
        ent.setRemark(vo.getRemark());
        ent.setServerNode(vo.getServerNode());
        updateById(ent);
        FollowTraderUserVO data = get(vo.getId());

        ThreadPoolUtils.execute(() -> {
            String token = req.getHeader("Authorization");
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", token);
            headers.put("Content-Type", "application/json");

            // 账号正常登录
            // 根据account和platform查询出对应的信息
            FollowTraderEntity followTraderEntity = followTraderService.list(
                            new LambdaQueryWrapper<FollowTraderEntity>()
                                    .eq(FollowTraderEntity::getAccount, data.getAccount())
                                    .eq(FollowTraderEntity::getPlatform, data.getPlatform()))
                    .getFirst();

            if (followTraderEntity != null) {
                FollowTraderVO followTraderVO = FollowTraderConvert.INSTANCE.convert(followTraderEntity);
                followTraderVO.setNewPassword(vo.getPassword());
                String url = MessageFormat.format("http://{0}:{1}{2}", followTraderEntity.getIpAddr(), FollowConstant.VPS_PORT, FollowConstant.VPS_RECONNECTION_Trader);
                RestTemplate restTemplate = new RestTemplate();

                // 使用提前提取的 headers 构建请求头
                HttpHeaders httpHeaders = new HttpHeaders();
                httpHeaders.setAll(headers);  // 注入提前获取的请求头
                HttpEntity<FollowTraderVO> entity = new HttpEntity<>(followTraderVO, httpHeaders);

                ResponseEntity<JSONObject> response = restTemplate.exchange(url, HttpMethod.POST, entity, JSONObject.class);
                if (response.getBody() != null && !response.getBody().getString("msg").equals("success")) {
                    log.error("账号重连失败: " + followTraderEntity.getAccount());
                }
            } else {
                log.error("未找到对应的 : 账号=" + data.getAccount() + "平台=" + data.getPlatform());
            }
        });
        }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);
        //根据id查询账号
        List<FollowTraderUserEntity> list = list(new LambdaQueryWrapper<FollowTraderUserEntity>().in(FollowTraderUserEntity::getId, idList));
        if (ObjectUtil.isNotEmpty(list)){
            List<String>accountList = list.stream().map(FollowTraderUserEntity::getAccount).toList();
            //删除followTrader表信息
            followTraderService.remove(new LambdaQueryWrapper<FollowTraderEntity>().in(FollowTraderEntity::getAccount, accountList));
        }

    }

    @Override
    public void export() {
    List<FollowTraderUserExcelVO> excelList = FollowTraderUserConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowTraderUserExcelVO.class, "账号初始表", null, excelList);
    }

    @Override
    public void generateCsv(ByteArrayOutputStream outputStream) throws IOException {
        // 使用相对路径替代绝对路径
        String inputFilePath = "/template/账号添加模板.csv"; // 相对于 resources 目录的路径

        // 读取 CSV 文件
        try (InputStream inputStream = getClass().getResourceAsStream(inputFilePath);
             Reader in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(in, CSVFormat.DEFAULT.withFirstRecordAsHeader());
             CSVPrinter csvPrinter = new CSVPrinter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {

            if (inputStream == null) {
                throw new FileNotFoundException("未找到指定的模板文件：" + inputFilePath);
            }

            // 获取第一行记录
            List<CSVRecord> records = parser.getRecords();

            // 检查是否有记录
            if (records.isEmpty()) {
                // 如果 CSV 文件为空，可以返回一个默认记录
                csvPrinter.printRecord("账号","密码","账号类型","服务器","节点","备注","排序");
            } else {
                CSVRecord firstRecord = records.get(0);
                String account = firstRecord.get("账号");
                String password = firstRecord.get("密码");
                String accountType = firstRecord.get("账号类型");
                String server = firstRecord.get("服务器");
                String node = firstRecord.get("节点");
                String remark = firstRecord.get("备注");
                String sort = firstRecord.get("排序");

                // 写入到输出流
                csvPrinter.printRecord(account, password, accountType, server, node, remark, sort);

            }
        }
    }

    @Override
    public void addByExcel(MultipartFile file, Long savedId) {
        //成功
        List<FollowTraderUserEntity> entityList = new ArrayList<>();
        //失败
        List<FollowFailureDetailEntity> failureList = new ArrayList<>();
        long successCount = 0;
        long failureCount = 0;
        try (InputStream inputStream = file.getInputStream();
             InputStreamReader reader = new InputStreamReader(inputStream, Charset.forName("GBK"));
//             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withSkipHeaderRecord())) {

            for (CSVRecord record : csvParser) {
                String account = record.get(0);
                String password = AesUtils.aesEncryptStr(record.get(1));
                String accountType = record.get(2).isEmpty() ? "MT4" : record.get(2).toUpperCase();
                String platform = record.get(3);
                String node = record.get(4);
                String remark = record.get(5);
                String sort = record.get(6).isEmpty() ? "1" : record.get(6);

                List<FollowTraderUserEntity> entities = list(new LambdaQueryWrapper<FollowTraderUserEntity>().eq(FollowTraderUserEntity::getAccount, account).eq(FollowTraderUserEntity::getPlatform, platform));
                if (ObjectUtil.isNotEmpty(entities)){
                    String errorRemark = "账号重复添加";
                    failureList.add(insertFailureDetail(account, accountType, platform, node, errorRemark,savedId));
                    failureCount++;
                    break;
                }

                // 校验必填字段
                StringBuilder errorMsg = new StringBuilder();
                if (account.isEmpty()) {
                    errorMsg.append("账号不能为空; ");
                }else {
                    if (followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, account)).size() > 0) {
                        errorMsg.append("账号已存在; ");
                    }
                }
                if (password.isEmpty()) {
                    errorMsg.append("密码不能为空; ");
                }
                if (platform.isEmpty()) {
                    errorMsg.append("服务器不能为空; ");
                }else {
                    if (followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, platform)).size() == 0) {
                        errorMsg.append("服务器不存在; ");
                    }
                }
                if (!accountType.equals("MT4") && !accountType.equals("MT5")) {
                    errorMsg.append("账号类型必须是MT4或MT5; ");
                }

                // 生成备注信息
                String errorRemark = errorMsg.length() > 0 ? errorMsg.toString() : remark;
                // 如果有错误，设置 upload_status 为 0
//                int uploadStatus = errorMsg.length() > 0 ? 0 : 1;
                if (errorMsg.length() == 0) {
                    entityList.add(insertAccount(account, password, accountType, platform, node, errorRemark,sort));
                    successCount++;
                } else {
                    failureList.add(insertFailureDetail(account, accountType, platform, node, errorRemark,savedId));
                    failureCount++;
                }
            }
            this.saveBatch(entityList);
            followFailureDetailService.saveBatch(failureList);
            LambdaUpdateWrapper<FollowUploadTraderUserEntity> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.set(FollowUploadTraderUserEntity::getSuccessCount, successCount)
                    .set(FollowUploadTraderUserEntity::getFailureCount, failureCount)
                    .set(FollowUploadTraderUserEntity::getUploadTotal, failureCount + successCount)
                    .set(FollowUploadTraderUserEntity::getStatus, TraderUserEnum.SUCCESS.getType())
                    .eq(FollowUploadTraderUserEntity::getId, savedId);
            followUploadTraderUserService.update(updateWrapper);
        } catch (Exception e) {
            log.error("处理Excel文件时发生错误: ", e);
        }
    }

    // 插入失败详情
    private FollowFailureDetailEntity insertFailureDetail(String account, String accountType, String platform, String node, String errorRemark, Long savedId) {
        FollowFailureDetailEntity entity = new FollowFailureDetailEntity();
        entity.setPlatformType(accountType);
        entity.setServer(platform);
        entity.setNode(node);
        entity.setAccount(account);
        entity.setRemark(errorRemark);
        entity.setRecordId(savedId);
        entity.setType(TraderUserTypeEnum.ADD_ACCOUNT.getType());
        return entity;
    }

    // 插入账号
    private FollowTraderUserEntity insertAccount(String account, String password, String accountType, String platform, String node, String errorRemark, String sort) {
        FollowTraderUserEntity entity = new FollowTraderUserEntity();
        entity.setAccount(account);
        entity.setPassword(password);
        entity.setAccountType(accountType);
        entity.setPlatform(platform);
        if (ObjectUtil.isNotEmpty(platform)){
            FollowPlatformEntity first = followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, platform)).getFirst();
            if (ObjectUtil.isNotEmpty(first)){
                entity.setPlatformId(Math.toIntExact(first.getId()));
            }
        }
        entity.setSort(Integer.parseInt(sort));
        entity.setServerNode(node);
        entity.setRemark(errorRemark);
        return entity;
    }

    @Override
    public void updateGroup(List<Long> idList, Long groupId) {
        FollowGroupVO vo = followGroupService.get(groupId);
        for (Long id : idList) {
            LambdaUpdateWrapper<FollowTraderUserEntity> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(FollowTraderUserEntity::getId, id)
                    .set(FollowTraderUserEntity::getGroupName, vo.getName())
                    .set(FollowTraderUserEntity::getGroupId, groupId);
            baseMapper.update(updateWrapper);
        }
    }

    public static void main(String[] args) {
        String s = AesUtils.aesEncryptStr("As123456");
        System.out.println(s);
    }
    @Override
    public void updatePasswords(List<FollowTraderUserVO> voList, String password, String confirmPassword, HttpServletRequest req) throws Exception {
        if (!password.equals(confirmPassword)) {
            throw new ServerException("两次密码输入不一致");
        }
        // 加密后
        String s = AesUtils.aesEncryptStr(password);

        // 设置状态
        FollowUploadTraderUserVO followUploadTraderUserVO = new FollowUploadTraderUserVO();
        followUploadTraderUserVO.setStatus(TraderUserEnum.IN_PROGRESS.getType());
        followUploadTraderUserVO.setOperator(SecurityUser.getUser().getUsername());
        followUploadTraderUserVO.setUploadTime(LocalDateTime.now());
        followUploadTraderUserVO.setType(TraderUserTypeEnum.MODIFY_PASSWORD.getType());
        followUploadTraderUserService.save(followUploadTraderUserVO);
        Long savedId = followUploadTraderUserService.getOne(new QueryWrapper<FollowUploadTraderUserEntity>().orderByDesc("id").last("limit 1")).getId();

        Integer uploadTotal = voList.size();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // 提前在主线程中获取 Token 和其他需要的头信息
        String token = req.getHeader("Authorization");
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", token);
        headers.put("Content-Type", "application/json");

        // 使用线程池
        ThreadPoolExecutor executor = (ThreadPoolExecutor) ThreadPoolUtils.getExecutor();

        CountDownLatch countDownLatch = new CountDownLatch(20);

        for (FollowTraderUserVO vo : voList) {
            executor.execute(() -> {
                try {
                    // 查询账号状态
                    LambdaQueryWrapper<FollowTraderEntity> queryWrapper = new LambdaQueryWrapper<>();
                    queryWrapper.eq(FollowTraderEntity::getAccount, vo.getAccount());
                    List<FollowTraderEntity> followTraderEntities = followTraderService.list(queryWrapper);
                    if (ObjectUtil.isNotEmpty(followTraderEntities)) {
                        for (FollowTraderEntity followTraderEntity : followTraderEntities) {
                            // 账号正常登录
                            FollowTraderVO followTraderVO = FollowTraderConvert.INSTANCE.convert(followTraderEntity);
                            followTraderVO.setNewPassword(password);
                            String url = MessageFormat.format("http://{0}:{1}{2}", followTraderEntity.getIpAddr(), FollowConstant.VPS_PORT, FollowConstant.VPS_RECONNECTION_Trader);
                            RestTemplate restTemplate = new RestTemplate();

                            // 使用提前提取的 headers 构建请求头
                            HttpHeaders httpHeaders = new HttpHeaders();
                            httpHeaders.setAll(headers);  // 注入提前获取的请求头
                            HttpEntity<FollowTraderVO> entity = new HttpEntity<>(followTraderVO, httpHeaders);

                            ResponseEntity<JSONObject> response = restTemplate.exchange(url, HttpMethod.POST, entity, JSONObject.class);
                            if (response.getBody() != null && !response.getBody().getString("msg").equals("success")) {
                                log.error("账号重连失败: " + followTraderEntity.getAccount());
                            }
                        }
                    }

                    // 更新traderUser密码并记录备注
                    LambdaUpdateWrapper<FollowTraderUserEntity> updateWrapper = new LambdaUpdateWrapper<>();
                    updateWrapper.eq(FollowTraderUserEntity::getId, vo.getId())
                            .set(FollowTraderUserEntity::getPassword, s);

                    baseMapper.update(updateWrapper);
                    successCount.incrementAndGet(); // 数据库更新成功算作成功
                } catch (Exception e) {
                    log.error("数据库更新失败: ", e);
                    FollowFailureDetailEntity failureDetail = new FollowFailureDetailEntity();
                    failureDetail.setPlatformType(vo.getAccountType());
                    failureDetail.setServer(vo.getPlatform());
                    failureDetail.setNode(vo.getServerNode());
                    failureDetail.setAccount(vo.getAccount());
                    failureDetail.setType(TraderUserTypeEnum.MODIFY_PASSWORD.getType());
                    failureDetail.setRecordId(savedId);
                    failureDetail.setRemark("数据库更新失败" + e);
                    followFailureDetailService.save(failureDetail);
                    failureCount.incrementAndGet(); // 数据库更新失败算作失败
                } finally {
                    countDownLatch.countDown();
                }
            });
        }

        countDownLatch.await(); // 等待所有任务完成

        followUploadTraderUserVO.setUploadTotal(uploadTotal);
        followUploadTraderUserVO.setSuccessCount(successCount.get());
        followUploadTraderUserVO.setFailureCount(failureCount.get());
        followUploadTraderUserVO.setStatus(TraderUserEnum.SUCCESS.getType());
        followUploadTraderUserVO.setId(savedId);
        followUploadTraderUserService.update(followUploadTraderUserVO);
    }


    @Override
    public void updatePassword(FollowTraderUserVO vo, String password, String confirmPassword, HttpServletRequest req) throws Exception {
        if (!password.equals(confirmPassword)) {
            throw new ServerException("两次密码输入不一致");
        }
        String s = AesUtils.aesEncryptStr(password);
        LambdaQueryWrapper<FollowTraderEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FollowTraderEntity::getAccount, vo.getAccount());
        List<FollowTraderEntity> followTraderEntities = followTraderService.list(queryWrapper);
        if (ObjectUtil.isNotEmpty(followTraderEntities)) {
            String token = req.getHeader("Authorization");
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", token);
            headers.put("Content-Type", "application/json");

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (FollowTraderEntity followTraderEntity : followTraderEntities) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    // 账号正常登录
                    FollowTraderVO followTraderVO = FollowTraderConvert.INSTANCE.convert(followTraderEntity);
                    followTraderVO.setNewPassword(password);
                    String url = MessageFormat.format("http://{0}:{1}{2}", followTraderEntity.getIpAddr(), FollowConstant.VPS_PORT, FollowConstant.VPS_RECONNECTION_Trader);
                    RestTemplate restTemplate = new RestTemplate();

                    // 使用提前提取的 headers 构建请求头
                    HttpHeaders httpHeaders = new HttpHeaders();
                    httpHeaders.setAll(headers);  // 注入提前获取的请求头
                    HttpEntity<FollowTraderVO> entity = new HttpEntity<>(followTraderVO, httpHeaders);

                    ResponseEntity<JSONObject> response = restTemplate.exchange(url, HttpMethod.POST, entity, JSONObject.class);
                    if (response.getBody() != null && !response.getBody().getString("msg").equals("success")) {
                        log.error("账号重连失败: " + followTraderEntity.getAccount());
                    }
                }, ThreadPoolUtils.getExecutor());

                futures.add(future);
            }

            // 等待所有异步任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        vo.setPassword(s);
        this.update(vo, req);
    }

    @Override
    public TraderUserStatVO getStatInfo(FollowTraderUserQuery query) {
        List<FollowTraderUserEntity> followTraderUserEntities = baseMapper.selectList(getWrapper(query));
        int size = followTraderUserEntities.size();
        Map<String,FollowTraderUserEntity> traderUserMap=new HashMap<>();
        followTraderUserEntities.forEach(o->{
            traderUserMap.put(o.getAccount() + "-" + o.getPlatformId(),o);
        });
        List<FollowTraderUserEntity> list = followTraderUserEntities.stream().filter(o -> o.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue())).toList();
        List<FollowTraderEntity> traders = followTraderService.list();
        Map<String,Integer> traderMap=new HashMap<>();
        TraderUserStatVO vo = TraderUserStatVO.builder().total(size).noVpsNum(list.size()).conNum(0).errNum(0).build();
        traders.stream().forEach(t->{
           if(t.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue())){
               FollowTraderUserEntity followTraderUserEntity = traderUserMap.get(t.getAccount() + "-" + t.getPlatformId());
               if(followTraderUserEntity!=null){
                   traderMap.put(t.getAccount() + "-" + t.getPlatformId(), 1);
               }

           }
        });
        vo.setConNum(traderMap.size());
        vo.setErrNum(size-list.size()-traderMap.size());
        return vo;
    }

    @Override
    public TraderUserStatVO searchPage(FollowTraderUserQuery query) {
        PageResult<FollowTraderUserVO> page = page(query);
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
        TraderUserStatVO statInfo = getStatInfo(query);
        list.forEach(o->{

            FollowPlatformEntity followPlatformEntity = platformMap.get(Long.parseLong(o.getPlatformId().toString()));
            o.setBrokerName(followPlatformEntity.getBrokerName());
            String key=o.getAccount() + "-" + o.getPlatformId();
            ArrayList<VpsDescVO> vpsDesc = new ArrayList<>();
            List<FollowTraderEntity> followTraderEntities = traderMap.get(key);
            if(o.getStatus().equals(CloseOrOpenEnum.OPEN.getValue())){
                AtomicReference<FollowRedisTraderVO> followRedisTraderVO = new AtomicReference<>();
                if(ObjectUtil.isNotEmpty(followTraderEntities)) {
                    followTraderEntities.forEach(f -> {
                        if (f.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue())) {
                            followRedisTraderVO.set((FollowRedisTraderVO) redisCache.get(Constant.TRADER_USER + f.getId()));
                        }
                        if (f.getType().equals(TraderTypeEnum.MASTER_REAL.getType())) {
                            VpsDescVO vo = VpsDescVO.builder().desc(f.getIpAddr() + "-" + vpsMap.get(f.getServerId()).getName() + "-跟单策略").statusExtra(f.getStatusExtra()).status(f.getStatus()).build();
                            vpsDesc.add(vo);
                        } else if (f.getType().equals(TraderTypeEnum.BARGAIN.getType())) {
                            VpsDescVO vo = VpsDescVO.builder().desc(f.getIpAddr() + "-" + vpsMap.get(f.getServerId()).getName() + "-交易分配").statusExtra(f.getStatusExtra()).status(f.getStatus()).build();
                            vpsDesc.add(vo);
                        } else {
                            VpsDescVO vo = VpsDescVO.builder().desc(f.getIpAddr() + "-" + vpsMap.get(f.getServerId()).getName() + "-跟单账号").statusExtra(f.getStatusExtra()).status(f.getStatus()).build();
                            vpsDesc.add(vo);

                        }
                    });
                }
                if(ObjectUtil.isEmpty(followRedisTraderVO.get())){
                    if(ObjectUtil.isNotEmpty(followTraderEntities)) {
                        Object o1 = redisCache.get(Constant.TRADER_USER + followTraderEntities.get(0).getId());
                        followRedisTraderVO.set((FollowRedisTraderVO) o1);
                    }

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
                    o.setLeverage(redisTraderVo.getLeverage());
                }
                o.setVpsDesc(vpsDesc);
            }
        });
        statInfo.setPageResult(page);
        return statInfo;
    }

    @Override
    public void hangVps(HangVpsVO hangVpsVO,HttpServletRequest request) {
        FollowVpsEntity vps = followVpsService.getById(hangVpsVO.getVpsId());
        if(ObjectUtil.isEmpty(vps)){
            throw new ServerException("vps不存在");
        }
        if(TraderTypeEnum.SLAVE_REAL.equals(hangVpsVO.getAccountType())){
            if(ObjectUtil.isEmpty(hangVpsVO.getTraderId())){
                throw new ServerException("喊单账号不能为空");
            }
        }
        //保存挂靠记录
        FollowUploadTraderUserVO followUploadTraderUserVO = new FollowUploadTraderUserVO();
        followUploadTraderUserVO.setStatus(TraderUserEnum.IN_PROGRESS.getType());
        followUploadTraderUserVO.setOperator(SecurityUser.getUser().getUsername());
        followUploadTraderUserVO.setUploadTime(LocalDateTime.now());
        followUploadTraderUserVO.setUploadTotal(hangVpsVO.getTraderUserIds().size());
        followUploadTraderUserService.save(followUploadTraderUserVO);
        //转发请求，检索账号
        List<FollowTraderUserEntity> followTraderUserEntities = listByIds(hangVpsVO.getTraderUserIds());
        HttpHeaders headerApplicationJsonAndToken = RestUtil.getHeaderApplicationJsonAndToken(request);
        //不用等待
        ThreadPoolUtils.getExecutor().execute(()-> {
            AtomicInteger sum = new AtomicInteger(0);
            AtomicInteger err = new AtomicInteger(0);

            CountDownLatch countDownLatch = new CountDownLatch(followTraderUserEntities.size());
            Vector<FollowFailureDetailEntity> errList = new Vector<>();

            followTraderUserEntities.forEach(f -> {
                ThreadPoolUtils.getExecutor().execute(() -> {
                    Result result = null;
                    //不是跟单账号，走策略新增接口
                    if (!TraderTypeEnum.SLAVE_REAL.equals(hangVpsVO.getAccountType())) {
                        FollowTraderVO vo = new FollowTraderVO();
                        vo.setTemplateId(hangVpsVO.getTemplateId());
                        vo.setAccount(f.getAccount());
                        vo.setPlatformId(f.getPlatformId());
                        vo.setPlatform(f.getPlatform());
                        vo.setFollowStatus(hangVpsVO.getFollowStatus());
                        vo.setPassword(f.getPassword());
                        vo.setType(hangVpsVO.getAccountType());
                        vo.setIsAdd(false);
                        result = RestUtil.sendRequest(request, vps.getIpAddress(), HttpMethod.POST, FollowConstant.ADD_TRADER, vo,headerApplicationJsonAndToken);
                    } else {
                        //策略转发
                        FollowAddSalveVo vo = new FollowAddSalveVo();
                        vo.setAccount(f.getAccount());
                        vo.setFollowClose(hangVpsVO.getFollowClose());
                        vo.setFollowDirection(hangVpsVO.getFollowDirection());
                        vo.setFollowMode(hangVpsVO.getFollowMode());
                        vo.setFollowOpen(hangVpsVO.getFollowOpen());
                        vo.setFollowParam(hangVpsVO.getFollowParam());
                        vo.setFollowRep(CloseOrOpenEnum.OPEN.getValue());
                        vo.setFollowStatus(hangVpsVO.getFollowStatus());
                        vo.setPassword(f.getPassword());
                        vo.setPlacedType(hangVpsVO.getPlacedType());
                        vo.setPlatform(f.getPlatform());
                        vo.setRemainder(hangVpsVO.getRemainder());
                        vo.setTemplateId(hangVpsVO.getTemplateId());
                        vo.setTraderId(hangVpsVO.getTraderId());
                        vo.setTemplateId(hangVpsVO.getTemplateId());
                        vo.setIsAdd(false);
                        //策略新增
                        result = RestUtil.sendRequest(request, vps.getIpAddress(), HttpMethod.POST, FollowConstant.ADD_SLAVE, vo,headerApplicationJsonAndToken);
                    }
                   if (result.getCode()==0){
                       sum.set(sum.get()+1);
                   }else{
                       err.set(err.get()+1);
                       FollowFailureDetailEntity entity = new FollowFailureDetailEntity();
                       entity.setPlatformType(f.getAccountType());
                       entity.setServer(f.getPlatform());
                       entity.setNode(f.getServerNode());
                       entity.setAccount(f.getAccount());
                       entity.setRemark(result.getMsg());
                       entity.setRecordId(followUploadTraderUserVO.getId());
                       entity.setType(TraderUserTypeEnum.ADD_ACCOUNT.getType());
                       errList.add(entity);
                   }
                    countDownLatch.countDown();
                });
                f.setStatus(CloseOrOpenEnum.OPEN.getValue());
            });
                try {
                    //等待
                    countDownLatch.await();
                    if(ObjectUtil.isEmpty(errList)){
                        followFailureDetailService.saveBatch(errList);
                    }

                    followUploadTraderUserVO.setFailureCount(err.get());
                    followUploadTraderUserVO.setSuccessCount(sum.get());
                    followUploadTraderUserVO.setStatus(TraderUserEnum.SUCCESS.getType());
                    followUploadTraderUserService.update(followUploadTraderUserVO);
                      updateBatchById(followTraderUserEntities);
                } catch (InterruptedException e) {
                       log.error("挂靠失败"+e);
                }


       });

    }
    private List<FollowTraderEntity> getByUserId(Long traderUserId) {
        FollowTraderUserEntity traderUser = baseMapper.selectById(traderUserId);
        List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, traderUser.getAccount()).eq(FollowTraderEntity::getPlatformId, traderUser.getPlatformId()));
        return list;
    }
    @Override
    public void belowVps(List<Long> traderUserIds, HttpServletRequest request) {
        List<FollowTraderUserEntity> followTraderUsers = baseMapper.selectBatchIds(traderUserIds);
        followTraderUsers.forEach(o->{
            o.setStatus(CloseOrOpenEnum.CLOSE.getValue());
        });
        updateBatchById(followTraderUsers);
        Map<String,List<Long>> map=new HashMap<>();

        followTraderUsers.forEach(traderUser->{
            List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, traderUser.getAccount()).eq(FollowTraderEntity::getPlatformId, traderUser.getPlatformId()));
            list.forEach(t->{
                if(!t.getType().equals(TraderTypeEnum.BARGAIN)){
                    List<Long> idList = map.get(t.getIpAddr());
                    if(idList==null){
                        idList=new ArrayList<>();
                    }
                    idList.add(t.getId());
                    map.put(t.getIpAddr(),idList);
                }
            });
        });
        if(ObjectUtil.isNotEmpty(map)){
            map.forEach((k,v)->{
                Result result = RestUtil.sendRequest(request, k, HttpMethod.DELETE, FollowConstant.DEL_TRADER, v,null);
            });
        }

    }

    @Override
    public void modify(List<FollowTraderUserVO> vos) {
        for (FollowTraderUserVO vo : vos) {
            FollowGroupVO group = followGroupService.get(Long.valueOf(vo.getGroupId()));
                LambdaUpdateWrapper<FollowTraderUserEntity> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(FollowTraderUserEntity::getId, vo.getId())
                        .set(FollowTraderUserEntity::getGroupName, group.getName())
                        .set(FollowTraderUserEntity::getGroupId, vo.getGroupId());
                baseMapper.update(updateWrapper);
        }
    }
}