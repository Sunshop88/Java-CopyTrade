package net.maku.followcom.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.convert.FollowTraderUserConvert;
import net.maku.followcom.dao.FollowOrderCloseDao;
import net.maku.followcom.dao.FollowTraderUserDao;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.enums.TraderUserEnum;
import net.maku.followcom.enums.TraderUserTypeEnum;
import net.maku.followcom.query.FollowTestServerQuery;
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
import org.apache.commons.lang3.math.NumberUtils;
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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
@Slf4j
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
    private final FollowBrokeServerService followBrokeServerService;
    private final FollowOrderCloseDao followOrderCloseDao;


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
            String remark = query.getRemark().replaceAll("%", "\\\\%");
            wrapper.like(FollowTraderUserEntity::getRemark,remark);
        }
       wrapper.eq(ObjectUtil.isNotEmpty(query.getHangStatus()),FollowTraderUserEntity::getStatus,query.getHangStatus());
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
                    statusExtra.add("经纪商异常");
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
        JSONArray accountVos = query.getAccountVos();
        if(ObjectUtil.isNotEmpty(accountVos)){
            StringBuilder sb = new StringBuilder();
            accountVos.forEach(o->{
                JSONObject json = JSONObject.parseObject(o.toString());
                String account=json.getString("account");
                String platformId = json.getString("platformId");
                sb.append("'"+account+"-"+platformId+"',");
            });
            String sql =sb.substring(0, sb.length() - 1);
            wrapper.apply("concat(account,'-',platform_id) in (" +sql+")");
        }
        //组别
        if(ObjectUtil.isNotEmpty(query.getGroupIds()) ){
            wrapper.in(FollowTraderUserEntity::getGroupId,query.getGroupIds());
        }
        //劵商名称
        if(ObjectUtil.isNotEmpty(query.getBrokerName())){
            String brokerName =query.getBrokerName().replaceAll("%", "\\\\%");
            List<FollowPlatformEntity> list = followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>().like(FollowPlatformEntity::getBrokerName, brokerName));
              if(ObjectUtil.isNotEmpty(list)){
                  List<Long> platformIds = list.stream().map(FollowPlatformEntity::getId).toList();
                  wrapper.in(FollowTraderUserEntity::getPlatformId,platformIds);
              }else{
                  wrapper.eq(FollowTraderUserEntity::getDeleted,2);
              }
        }
        //  服务器
        if (ObjectUtil.isNotEmpty(query.getPlatform())){
            String platform = query.getPlatform().replaceAll("%", "\\\\%");
            wrapper.like(FollowTraderUserEntity::getPlatform,platform);
        }
        if (ObjectUtil.isNotEmpty(query.getAccount())){
            String account = query.getAccount().replaceAll("%", "\\\\%");
            wrapper.like(FollowTraderUserEntity::getAccount,account);
        }
        if (ObjectUtil.isNotEmpty(query.getGroupName())){
            wrapper.in(FollowTraderUserEntity::getGroupName,query.getGroupName());
        }


        wrapper.orderByDesc(FollowTraderUserEntity::getId);


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
        entity.setPassword(entity.getPassword());
        if (ObjectUtil.isEmpty(vo.getSort())){
            entity.setSort(1);
        }

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowTraderUserVO vo, HttpServletRequest req) {
        HttpHeaders headerApplicationJsonAndToken = RestUtil.getHeaderApplicationJsonAndToken(req);
        ThreadPoolUtils.execute(() -> {
            try {
            /*    String token = req.getHeader("Authorization");
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", token);
                headers.put("Content-Type", "application/json");*/

                // 根据account和platform查询出对应的信息
                List<FollowTraderEntity> followTraderEntities = followTraderService.list(
                                new LambdaQueryWrapper<FollowTraderEntity>()
                                        .eq(FollowTraderEntity::getAccount, vo.getAccount())
                                        .eq(FollowTraderEntity::getPlatform, vo.getPlatform()));

                if (ObjectUtil.isNotEmpty(followTraderEntities) && !followTraderEntities.getFirst().getPassword().equals(vo.getPassword())) {
                    FollowTraderEntity followTraderEntity = followTraderEntities.getFirst();
                    FollowTraderVO followTraderVO = FollowTraderConvert.INSTANCE.convert(followTraderEntity);
                    followTraderVO.setNewPassword(vo.getPassword());

                    Result response = RestUtil.sendRequest(req, followTraderVO.getIpAddr(), HttpMethod.POST, FollowConstant.VPS_RECONNECTION_UPDATE_TRADER, followTraderVO, headerApplicationJsonAndToken);
                    if (response != null && !response.getMsg().equals("success")) {
                        log.error(followTraderEntity.getAccount() + "账号重连失败: " + response.getMsg());
                    } else {
                        log.info("账号重连成功: {}", followTraderEntity.getAccount());
                    }
                }else {
                    //修改密码
                    FollowTraderUserEntity entity = new FollowTraderUserEntity();
                    entity.setId(vo.getId());
                    entity.setPassword(vo.getPassword());
                    updateById(entity);
                }
            } catch (Exception e) {
                log.error("异步任务执行过程中发生异常", e);
            }
        });
        //只修改密码和备注信息
        FollowTraderUserEntity ent = new FollowTraderUserEntity();
        ent.setId(vo.getId());
        ent.setRemark(vo.getRemark());
        ent.setServerNode(vo.getServerNode());
        ent.setSort(vo.getSort());
        updateById(ent);

        }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList, HttpServletRequest request) {
        for (Long id : idList) {
            FollowTraderUserVO vo = get(id);
            //根据账号删除
            List<FollowTraderEntity> List= followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, vo.getAccount()).eq(FollowTraderEntity::getPlatformId, vo.getPlatformId()));
            if (ObjectUtil.isNotEmpty(List)){
                //查看喊单账号是否存在用户
                List<FollowTraderSubscribeEntity> followTraderSubscribeEntityList = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().in(FollowTraderSubscribeEntity::getMasterId, List.stream().map(FollowTraderEntity::getId).toList()));
                if (ObjectUtil.isNotEmpty(followTraderSubscribeEntityList)) {
                    log.info("跟单账号有绑定:{}", List.getFirst().getAccount());
//                    throw new ServerException("请先删除跟单用户:{}");
                    continue;
                }
                for (FollowTraderEntity entity : List) {
                    List<Long> account = Collections.singletonList(entity.getId());
                    Result result = RestUtil.sendRequest(request, entity.getIpAddr(), HttpMethod.DELETE, FollowConstant.DEL_TRADER, account,null);
                    if(result.getCode()!=CloseOrOpenEnum.CLOSE.getValue()){
                        throw new ServerException(result.getMsg());
                    }
                    log.info("删除成功:{}", result);
                }
            }
            removeById(id);
        }
//        removeByIds(idList);

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
            // 写入 UTF-8 BOM
            outputStream.write("\uFEFF".getBytes(StandardCharsets.UTF_8));
            // 获取第一行记录
            List<CSVRecord> records = parser.getRecords();

            // 检查是否有记录
            if (records.isEmpty()) {
                // 如果 CSV 文件为空，可以返回一个默认记录
                csvPrinter.printRecord("账号", "密码", "账号类型", "服务器", "节点", "备注", "排序");
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
    public void addByExcel(MultipartFile file, Long savedId ,List<FollowTestDetailVO> vos) {
            //成功
            List<FollowTraderUserEntity> entityList = new ArrayList<>();
            //失败
            List<FollowFailureDetailEntity> failureList = new ArrayList<>();
            List<FollowTraderUserVO> paramsList = new ArrayList<>();
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

                    StringBuilder errorMsg = new StringBuilder();
                    if (!accountType.equals("MT4") && !accountType.equals("MT5")) {
                        errorMsg.append("账号类型必须是MT4或MT5; ");
                    }
//                    }else {
//                        if (accountType.equals("MT5")) {
//                            accountType = CloseOrOpenEnum.OPEN.getValue() + "";
//                        }
//                        if (accountType.equals("MT4")) {
//                            accountType = CloseOrOpenEnum.CLOSE.getValue() + "";
//                        }
//                    }

                    List<FollowTraderUserEntity> entities = list(new LambdaQueryWrapper<FollowTraderUserEntity>().eq(FollowTraderUserEntity::getAccount, account).eq(FollowTraderUserEntity::getPlatform, platform));
                    if (ObjectUtil.isNotEmpty(entities)) {
                        String errorRemark = "账号已存在;";
                        failureList.add(insertFailureDetail(account, accountType, platform, node, errorRemark, savedId));
                        failureCount++;
                        continue;
                    }

                    // 校验必填字段
//                    StringBuilder errorMsg = new StringBuilder();
                    if (account.isEmpty()) {
                        errorMsg.append("账号不能为空; ");
                    }else if (!NumberUtils.isDigits(account)){
                        errorMsg.append("账号需为数字; ");
                    }
//                }else {
//                    if (followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, account)).size() > 0) {
//                        errorMsg.append("账号已存在; ");
//                    }
//                }
                    if (password.isEmpty()) {
                        errorMsg.append("密码不能为空; ");
                    } else if (record.get(1).length() < 6 || record.get(1).length() > 16) {
                        errorMsg.append("密码需在6 ~ 16位之间; ");
                    }
                    if (platform.isEmpty()) {
                        errorMsg.append("服务器不能为空; ");
                    } else {
                        if (followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, platform)).size() == 0) {
                            errorMsg.append("服务器不存在; ");
                        }
                    }
//                    if (!accountType.equals("0") && !accountType.equals("1")) {
//                        errorMsg.append("账号类型必须是MT4或MT5; ");
//                    }
                    if (ObjectUtil.isNotEmpty(node) && ObjectUtil.isNotEmpty(platform)) {
                        //将node拆分
                        String[] split = node.split(":");
                        if (split.length != 2) {
                            errorMsg.append("节点格式不正确; ");
                        } else if (followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>()
                                .eq(FollowBrokeServerEntity::getServerName, platform)
                                .eq(FollowBrokeServerEntity::getServerNode, split[0])
                                .eq(FollowBrokeServerEntity::getServerPort, split[1])).size() == 0) {
                            errorMsg.append("节点不存在; ");
                        }
                    }
                    if (ObjectUtil.isEmpty(node) && ObjectUtil.isNotEmpty(platform)){
                        //根据platform 查询默认节点
                        List<FollowTestDetailVO> list = vos.stream().filter(vo -> vo.getServerName().equals(platform)).toList();
                        if (ObjectUtil.isNotEmpty(list)){
                            List<FollowTestDetailVO> list1 = list.stream().filter(vo -> vo.getIsDefaultServer() == 0).toList();
                            if (ObjectUtil.isNotEmpty(list1)){
                            node = list1.get(0).getServerNode();
                            }else {
                                node = list.get(0).getServerNode();
                            }
                        }
                    }

                    // 生成备注信息
                    String errorRemark = errorMsg.length() > 0 ? errorMsg.toString() : remark;
                    // 如果有错误，设置 upload_status 为 0
//                int uploadStatus = errorMsg.length() > 0 ? 0 : 1;
                    if (errorMsg.length() == 0) {
                        if (accountType.equals("MT5")) {
                            accountType = CloseOrOpenEnum.OPEN.getValue() + "";
                        }
                        if (accountType.equals("MT4")) {
                            accountType = CloseOrOpenEnum.CLOSE.getValue() + "";
                        }
                        entityList.add(insertAccount(account, password, accountType, platform, node, errorRemark, sort));
                        successCount++;
                    } else {
                        failureList.add(insertFailureDetail(account, accountType, platform, node, errorRemark, savedId));
                        failureCount++;
                    }
                }
                if (ObjectUtil.isNotEmpty(entityList)) {
                    //批量保存前还要检查一次并发重复的数据，platform和account同时相等的情况
                    entityList = entityList.stream()
                            .collect(Collectors.toMap(
                                    entity -> entity.getAccount() + "_" + entity.getPlatform(),
                                    entity -> entity,
                                    (existing, replacement) -> existing))
                            .values()
                            .stream()
                            .collect(Collectors.toList());  // 最终转换成 list
                    this.saveBatch(entityList);
                }
                if (ObjectUtil.isNotEmpty(failureList)) {
                    followFailureDetailService.saveBatch(failureList);
                }
                ObjectMapper objectMapper = new ObjectMapper();
                String json = null;
                try {
                    json = objectMapper.writeValueAsString(failureList);
                } catch (JsonProcessingException e) {
                    log.error("挂靠参数序列异常:"+e);
                }
                LambdaUpdateWrapper<FollowUploadTraderUserEntity> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.set(FollowUploadTraderUserEntity::getSuccessCount, successCount)
                        .set(FollowUploadTraderUserEntity::getFailureCount, failureCount)
                        .set(FollowUploadTraderUserEntity::getUploadTotal, failureCount + successCount)
                        .set(FollowUploadTraderUserEntity::getStatus, TraderUserEnum.SUCCESS.getType())
                        .set(FollowUploadTraderUserEntity::getParams,json)
                        .eq(FollowUploadTraderUserEntity::getId, savedId);
                followUploadTraderUserService.update(updateWrapper);
        }catch (Exception e) {
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
        if (ObjectUtil.isEmpty(vo)){
            throw new ServerException("分组不存在");
        }
        for (Long id : idList) {
            LambdaUpdateWrapper<FollowTraderUserEntity> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(FollowTraderUserEntity::getId, id)
                    .set(FollowTraderUserEntity::getGroupName, vo.getName())
                    .set(FollowTraderUserEntity::getGroupId, groupId);
            baseMapper.update(updateWrapper);
        }
    }

    public static void main(String[] args) {
        String s = AesUtils.aesEncryptStr("e88ef3200461457004569f2480d7d9c2");
        String s1 = AesUtils.decryptStr("0cc3bcc3558479b77f645741621dde0c");
        System.out.println(s);
        System.out.println(s1);
    }
    @Override
    public void updatePasswords(List<FollowTraderUserVO> voList, String password, String confirmPassword, HttpServletRequest req) throws Exception {
        HttpHeaders headerApplicationJsonAndToken = RestUtil.getHeaderApplicationJsonAndToken(req);
        if (!password.equals(confirmPassword)) {
            throw new ServerException("两次密码输入不一致");
        }
        // 加密后
//        String s = AesUtils.aesEncryptStr(password);

        // 设置状态
        FollowUploadTraderUserVO followUploadTraderUserVO = new FollowUploadTraderUserVO();
        followUploadTraderUserVO.setStatus(TraderUserEnum.IN_PROGRESS.getType());
        followUploadTraderUserVO.setOperator(SecurityUser.getUser().getUsername());
        followUploadTraderUserVO.setUploadTime(LocalDateTime.now());
        followUploadTraderUserVO.setType(TraderUserTypeEnum.MODIFY_PASSWORD.getType());
        followUploadTraderUserService.save(followUploadTraderUserVO);
        Long savedId = followUploadTraderUserService.getOne(new QueryWrapper<FollowUploadTraderUserEntity>().orderByDesc("id").last("limit 1")).getId();

//        Integer uploadTotal = null;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

//        // 提前在主线程中获取 Token 和其他需要的头信息
//        String token = req.getHeader("Authorization");
//        Map<String, String> headers = new HashMap<>();
//        headers.put("Authorization", token);
//        headers.put("Content-Type", "application/json");

        // 使用 CompletableFuture 处理异步任务
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (FollowTraderUserVO vo : voList) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
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
//                            String url = MessageFormat.format("http://{0}:{1}{2}", followTraderEntity.getIpAddr(), FollowConstant.VPS_PORT, FollowConstant.VPS_RECONNECTION_Trader);
//                            RestTemplate restTemplate = new RestTemplate();

                            // 使用提前提取的 headers 构建请求头
//                            HttpHeaders httpHeaders = new HttpHeaders();
//                            httpHeaders.setAll(headers);  // 注入提前获取的请求头
//                            HttpEntity<FollowTraderVO> entity = new HttpEntity<>(followTraderVO, httpHeaders);

//                            ResponseEntity<JSONObject> response = restTemplate.exchange(url, HttpMethod.POST, entity, JSONObject.class);
                            Result response = RestUtil.sendRequest(req, followTraderVO.getIpAddr(), HttpMethod.POST, FollowConstant.VPS_RECONNECTION_Trader, followTraderVO, headerApplicationJsonAndToken);
//                            if (response.getBody() != null && !response.getBody().getString("msg").equals("success")) {
                            if (response != null && !response.getMsg().equals("success")) {
                                log.error(followTraderEntity.getAccount() + "账号重连失败: " + response.getMsg());
                                FollowFailureDetailEntity failureDetail = new FollowFailureDetailEntity();
                                failureDetail.setPlatformType(vo.getAccountType());
                                failureDetail.setServer(vo.getPlatform());
                                failureDetail.setNode(vo.getServerNode());
                                failureDetail.setAccount(vo.getAccount());
                                failureDetail.setType(TraderUserTypeEnum.MODIFY_PASSWORD.getType());
                                failureDetail.setRecordId(savedId);
                                // 报错内容
                                failureDetail.setRemark(response.getMsg());
                                followFailureDetailService.save(failureDetail);
                                failureCount.incrementAndGet(); // 数据库更新失败算作失败
                                log.error("账号重连失败: " + followTraderEntity.getAccount());
                            }else{
                                successCount.incrementAndGet(); // 数据库更新成功算作成功
                            }
                        }
                    } else {
                        // 更新traderUser密码并记录备注
                        LambdaUpdateWrapper<FollowTraderUserEntity> updateWrapper = new LambdaUpdateWrapper<>();
                        updateWrapper.eq(FollowTraderUserEntity::getId, vo.getId())
                                .set(FollowTraderUserEntity::getPassword, password);
                        baseMapper.update(updateWrapper);
                        successCount.incrementAndGet(); // 数据库更新成功算作成功
                    }
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
                }
            });

            futures.add(future);
        }

        // 等待所有任务完成
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        allOf.join(); // 阻塞直到所有任务完成

        followUploadTraderUserVO.setUploadTotal(successCount.get() + failureCount.get());
        followUploadTraderUserVO.setSuccessCount(successCount.get());
        followUploadTraderUserVO.setFailureCount(failureCount.get());
        followUploadTraderUserVO.setStatus(TraderUserEnum.SUCCESS.getType());
        followUploadTraderUserVO.setId(savedId);
        followUploadTraderUserService.update(followUploadTraderUserVO);
    }




    @Override
    public void updatePassword(FollowTraderUserVO vo, String password, String confirmPassword, HttpServletRequest req) throws Exception {
        HttpHeaders headerApplicationJsonAndToken = RestUtil.getHeaderApplicationJsonAndToken(req);
//        String s = AesUtils.aesEncryptStr(password);
        LambdaQueryWrapper<FollowTraderEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FollowTraderEntity::getAccount, vo.getAccount());
        List<FollowTraderEntity> followTraderEntities = followTraderService.list(queryWrapper);
        if (ObjectUtil.isNotEmpty(followTraderEntities)) {

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

            for (FollowTraderEntity followTraderEntity : followTraderEntities) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                    // 账号正常登录
                    FollowTraderVO followTraderVO = FollowTraderConvert.INSTANCE.convert(followTraderEntity);
                    followTraderVO.setNewPassword(password);
                    Result response = RestUtil.sendRequest(req, followTraderVO.getIpAddr(), HttpMethod.POST, FollowConstant.VPS_RECONNECTION_Trader, followTraderVO, headerApplicationJsonAndToken);
//                    if (response.getBody() != null && !response.getBody().getString("msg").equals("success")) {
                    if (response != null && !response.getMsg().equals("success")) {
                        log.error(followTraderEntity.getAccount() + "账号重连失败: " + response.getMsg());
                        throw new ServerException(followTraderEntity.getAccount() + "账号重连失败: " + response.getMsg());
                    }
                    } catch (Exception e) {
                        log.error("账号重连过程中发生异常: ", e);
                        exceptions.add(e);
                    }
                }, ThreadPoolUtils.getExecutor());

                futures.add(future);
            }

            // 等待所有异步任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            // 检查是否有异常发生
            if (!exceptions.isEmpty()) {
                // 抛出第一个异常
                throw exceptions.get(0);
            }
        } else {
            vo.setPassword(password);
            FollowTraderUserEntity convert = FollowTraderUserConvert.INSTANCE.convert(vo);
            this.updateById(convert);
        }
    }

    @Override
    public TraderUserStatVO getStatInfo(FollowTraderUserQuery query) {
        long startTime = System.currentTimeMillis();
        AtomicReference<List<FollowTraderEntity>> traders= new AtomicReference<>();
        Map<String,Integer> traderMap=new HashMap<>();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ThreadPoolUtils.getExecutor().execute(()->{
           traders.set(followTraderService.list());
            countDownLatch.countDown();
        });

        List<FollowTraderUserEntity> followTraderUserEntities = baseMapper.selectList(getWrapper(query));
        int size = followTraderUserEntities.size();
        Map<String,FollowTraderUserEntity> traderUserMap=new HashMap<>();
        followTraderUserEntities.forEach(o->{
            traderUserMap.put(o.getAccount() + "-" + o.getPlatformId(),o);
        });
        List<FollowTraderUserEntity> list = followTraderUserEntities.stream().filter(o -> o.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue())).toList();


        TraderUserStatVO vo = TraderUserStatVO.builder().total(size).noVpsNum(list.size()).conNum(0).errNum(0).build();
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            throw new ServerException("查询异常",e);
        }
        traders.get().stream().forEach(t->{
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
      //  long startTime = System.currentTimeMillis();
        CountDownLatch countDownLatch = new CountDownLatch(5);
        AtomicReference<PageResult<FollowTraderUserVO>> pageAtomic= new AtomicReference<>();
        ThreadPoolUtils.getExecutor().execute(()->{
            pageAtomic.set(page(query));
            countDownLatch.countDown();
        });
        Map<Long,FollowTraderSubscribeEntity> subscribeMap=new HashMap<>();
        ThreadPoolUtils.getExecutor().execute(()->{
            List<FollowTraderSubscribeEntity> subscribes = followTraderSubscribeService.list();
            subscribes.stream().forEach(s->{
                subscribeMap.put(s.getSlaveId(),s);
            });
            countDownLatch.countDown();
                });
        Map<String,List<FollowTraderEntity>> traderMap=new ConcurrentHashMap<>();

       ThreadPoolUtils.getExecutor().execute(()->{
           List<FollowTraderEntity> traders = followTraderService.list();
           traders.stream().forEach(t->{
               List<FollowTraderEntity> followTraderEntities = traderMap.get(t.getAccount() + "-" + t.getPlatformId());
               if (ObjectUtil.isEmpty(followTraderEntities)) {
                   followTraderEntities = new ArrayList<>();

               }
               followTraderEntities.add(t);
               traderMap.put(t.getAccount() + "-" + t.getPlatformId(), followTraderEntities);
           });
            countDownLatch.countDown();
        });
        Map<Integer,FollowVpsEntity> vpsMap=new HashMap<>();
        AtomicReference<Map<Long, FollowPlatformEntity>> platformMap= new AtomicReference<>(new HashMap<>());
        ThreadPoolUtils.getExecutor().execute(()->{
        List<FollowVpsEntity> vpsList = followVpsService.list();
        List<FollowPlatformEntity> platforms = followPlatformService.list();
            vpsList.forEach(v->{
                vpsMap.put(v.getId(),v);
            });
            platformMap.set(platforms.stream().collect(Collectors.toMap(FollowPlatformEntity::getId, Function.identity())));
            countDownLatch.countDown();
        });
        AtomicReference<TraderUserStatVO> statInfo= new AtomicReference<>();
        ThreadPoolUtils.getExecutor().execute(()->{
             statInfo.set(getStatInfo(query));
             statInfo.get().setParagraph(new AtomicBigDecimal(BigDecimal.ZERO));
            countDownLatch.countDown();
        });
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            throw new ServerException("查询异常"+e.getMessage());
        }
       /* long endTime = System.currentTimeMillis();
        long duration = (endTime - startTime);
        System.out.println("分页运行时间: " + duration + " 毫秒");*/
        PageResult<FollowTraderUserVO> page = pageAtomic.get();
        List<FollowTraderUserVO> list = page.getList();
        CountDownLatch count = new CountDownLatch(list.size());
        List<FollowGroupEntity> groups = followGroupService.list();
        Map<Integer, FollowGroupEntity> groupMap = groups.stream().collect(Collectors.toMap(FollowGroupEntity::getId, Function.identity()));

        list.forEach(o->{
            ThreadPoolUtils.getExecutor().execute(()->{
                if(ObjectUtil.isNotEmpty(o.getGroupId())){
                    String color = groupMap.get(o.getGroupId()).getColor();
                   o.setGroupColor(color);
                }
                try {
//                    o.setPassword(AesUtils.decryptStr(o.getPassword()));
                    o.setPassword(o.getPassword());
                } catch (Exception e) {
                    o.setPassword(o.getPassword());
                }
             FollowPlatformEntity followPlatformEntity = platformMap.get().get(Long.parseLong(o.getPlatformId().toString()));

            if(ObjectUtil.isNotEmpty(followPlatformEntity)){
                o.setBrokerName(followPlatformEntity.getBrokerName());
            }
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
                    o.setProfit(redisTraderVo.getProfit());
                    statInfo.get().getParagraph().add(o.getFreeMargin());
                }
                o.setVpsDesc(vpsDesc);
            }
                count.countDown();
            });
        });
        try {
            count.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        statInfo.get().setPageResult(page);
        return statInfo.get();
    }

    @Override
    public void synchData(Long traderId,HttpServletRequest request) {
        List<FollowTraderEntity> list = getByUserId(traderId);
        if(ObjectUtil.isNotEmpty(list)){
            FollowTraderEntity followTraderEntity = list.get(0);
            Result result = RestUtil.sendRequest(request, followTraderEntity.getIpAddr(), HttpMethod.POST, FollowConstant.SYNCH_DATA+followTraderEntity.getId(), null, null);
        }
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
        followUploadTraderUserVO.setType(TraderUserTypeEnum.ATTACH_VPS.getType());
        followUploadTraderUserService.save(followUploadTraderUserVO);
        //转发请求，检索账号
        List<FollowTraderUserEntity> followTraderUserEntities = listByIds(hangVpsVO.getTraderUserIds());
        HttpHeaders headerApplicationJsonAndToken = RestUtil.getHeaderApplicationJsonAndToken(request);
        //不用等待
        List<Long> error=new ArrayList<>();
        ThreadPoolUtils.getExecutor().execute(()-> {
            AtomicInteger sum = new AtomicInteger(0);
            AtomicInteger err = new AtomicInteger(0);
            Vector<Long> traderUserIds = new Vector<Long>();

            CountDownLatch countDownLatch = new CountDownLatch(followTraderUserEntities.size());
            Vector<FollowFailureDetailEntity> errList = new Vector<>();

            followTraderUserEntities.forEach(f -> {
                ThreadPoolUtils.getExecutor().execute(() -> {
                    Result result = null;
                    //不是跟单账号，走策略新增接口
                    if (!TraderTypeEnum.SLAVE_REAL.getType().equals(hangVpsVO.getAccountType())) {
                        FollowTraderVO vo = new FollowTraderVO();
                        vo.setTemplateId(hangVpsVO.getTemplateId());
                        vo.setAccount(f.getAccount());
                        vo.setPlatformId(f.getPlatformId());
                        vo.setPlatform(f.getPlatform());
                        vo.setFollowStatus(hangVpsVO.getFollowStatus());
//                        vo.setPassword(AesUtils.decryptStr(f.getPassword()));
                        vo.setPassword(f.getPassword());
                        vo.setType(hangVpsVO.getAccountType());
                        vo.setIsAdd(false);
                        vo.setIsSyncLogin(true);
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
//                        vo.setPassword(AesUtils.decryptStr(f.getPassword()));
                        vo.setPassword(f.getPassword());
                        vo.setPlacedType(hangVpsVO.getPlacedType());
                        vo.setPlatform(f.getPlatform());
                        vo.setRemainder(hangVpsVO.getRemainder());
                        vo.setTemplateId(hangVpsVO.getTemplateId());
                        vo.setTraderId(hangVpsVO.getTraderId());
                        vo.setTemplateId(hangVpsVO.getTemplateId());
                        vo.setFixedComment(hangVpsVO.getFixedComment());
                        vo.setCommentType(hangVpsVO.getCommentType());
                        vo.setDigits(hangVpsVO.getDigits());
                        vo.setIsAdd(false);
                        vo.setIsSyncLogin(true);
                        //策略新增
                        result = RestUtil.sendRequest(request, vps.getIpAddress(), HttpMethod.POST, FollowConstant.ADD_SLAVE, vo,headerApplicationJsonAndToken);
                    }
                   if (result.getCode()==0){
                       sum.set(sum.get()+1);
                   }else{
                       err.set(err.get()+1);
                       traderUserIds.add(f.getId());
                       FollowFailureDetailEntity entity = new FollowFailureDetailEntity();
                       entity.setPlatformType(f.getAccountType());
                       entity.setServer(f.getPlatform());
                       entity.setNode(f.getServerNode());
                       entity.setAccount(f.getAccount());
                       if(result.getMsg().contains("DataIntegrityViolationException")){
                           entity.setRemark("从库异常");
                       }
                       if(result.getMsg().length()>100){
                           entity.setRemark("系统错误");
                       }else{
                           entity.setRemark(result.getMsg());
                       }

                       entity.setRecordId(followUploadTraderUserVO.getId());
                       entity.setType(TraderUserTypeEnum.ATTACH_VPS.getType());
                       errList.add(entity);
                       //查找这个账号有没有
                       List<FollowTraderEntity> traders = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, f.getAccount()).eq(FollowTraderEntity::getPlatformId, f.getPlatformId()));
                       if(ObjectUtil.isEmpty(traders)){
                        //   f.setStatus(CloseOrOpenEnum.CLOSE.getValue());
                           error.add(f.getId());
                       }


                   }
                    countDownLatch.countDown();
                });
                f.setStatus(CloseOrOpenEnum.OPEN.getValue());
            });
                try {
                    //等待
                    countDownLatch.await();
                    if(ObjectUtil.isNotEmpty(errList)){
                        followFailureDetailService.saveBatch(errList);
                    }
                    followUploadTraderUserVO.setFailureCount(err.get());
                    followUploadTraderUserVO.setSuccessCount(sum.get());
                    followUploadTraderUserVO.setStatus(TraderUserEnum.SUCCESS.getType());
                    hangVpsVO.setTraderUserIds(traderUserIds);
                    ObjectMapper objectMapper = new ObjectMapper();
                    String json = null;
                    try {
                        json = objectMapper.writeValueAsString(hangVpsVO);
                    } catch (JsonProcessingException e) {
                       log.error("挂靠参数序列异常:"+e);
                    }
                    followUploadTraderUserVO.setParams(json);
                    followUploadTraderUserService.update(followUploadTraderUserVO);
                    updateBatchById(followTraderUserEntities);
                    if(ObjectUtil.isNotEmpty(error)){
                        update(new  LambdaUpdateWrapper<FollowTraderUserEntity>().in(FollowTraderUserEntity::getId,error).set(FollowTraderUserEntity::getStatus,CloseOrOpenEnum.CLOSE.getValue()));
                    }
                } catch (InterruptedException e) {
                       log.error("挂靠失败"+e);
                }
       });

    }

    @Override
    public void retryHangVps(Long uploadId,HttpServletRequest request) {
        FollowUploadTraderUserEntity followUploadTraderUserEntity = followUploadTraderUserService.getById(uploadId);
        String params = followUploadTraderUserEntity.getParams();
        if (ObjectUtil.isEmpty(params)) {
            //无法失败记录或无失败参数
            throw new ServerException("无失败记录");
        }
        HangVpsVO hangVpsVO = JSON.parseObject(params, HangVpsVO.class);
        FollowVpsEntity vps = followVpsService.getById(hangVpsVO.getVpsId());
        if(ObjectUtil.isEmpty(vps)){
            throw new ServerException("vps不存在");
        }
        if(TraderTypeEnum.SLAVE_REAL.equals(hangVpsVO.getAccountType())){
            if(ObjectUtil.isEmpty(hangVpsVO.getTraderId())){
                throw new ServerException("喊单账号不能为空");
            }
        }
        followUploadTraderUserEntity.setStatus(TraderUserEnum.IN_PROGRESS.getType());
        followUploadTraderUserEntity.setFailureCount(0l);
        followUploadTraderUserService.updateById(followUploadTraderUserEntity);
        followFailureDetailService.remove(new LambdaQueryWrapper<FollowFailureDetailEntity>().eq(FollowFailureDetailEntity::getRecordId,followUploadTraderUserEntity.getId()));
        List<FollowTraderUserEntity> followTraderUserEntities = listByIds(hangVpsVO.getTraderUserIds());
        HttpHeaders headerApplicationJsonAndToken = RestUtil.getHeaderApplicationJsonAndToken(request);
        //不用等待
        List<Long> error=new ArrayList<>();
        ThreadPoolUtils.getExecutor().execute(()-> {
            AtomicInteger sum = new AtomicInteger(0);
            AtomicInteger err = new AtomicInteger(0);
            Vector<Long> traderUserIds = new Vector<Long>();
            CountDownLatch countDownLatch = new CountDownLatch(followTraderUserEntities.size());
            Vector<FollowFailureDetailEntity> errList = new Vector<>();
            followTraderUserEntities.forEach(f -> {
                ThreadPoolUtils.getExecutor().execute(() -> {
                    Result result = null;
                    //不是跟单账号，走策略新增接口
                    if (!TraderTypeEnum.SLAVE_REAL.getType().equals(hangVpsVO.getAccountType())) {
                        FollowTraderVO vo = new FollowTraderVO();
                        vo.setTemplateId(hangVpsVO.getTemplateId());
                        vo.setAccount(f.getAccount());
                        vo.setPlatformId(f.getPlatformId());
                        vo.setPlatform(f.getPlatform());
                        vo.setFollowStatus(hangVpsVO.getFollowStatus());
                        vo.setPassword(f.getPassword());
                        vo.setType(hangVpsVO.getAccountType());
                        vo.setIsAdd(false);
                        vo.setIsSyncLogin(true);
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
                        vo.setFixedComment(hangVpsVO.getFixedComment());
                        vo.setCommentType(hangVpsVO.getCommentType());
                        vo.setDigits(hangVpsVO.getDigits());
                        vo.setIsAdd(false);
                        vo.setIsSyncLogin(true);
                        //策略新增
                        result = RestUtil.sendRequest(request, vps.getIpAddress(), HttpMethod.POST, FollowConstant.ADD_SLAVE, vo,headerApplicationJsonAndToken);
                    }
                    if (result.getCode()==0){
                        sum.set(sum.get()+1);
                    }else{
                        err.set(err.get()+1);
                        traderUserIds.add(f.getId());
                        FollowFailureDetailEntity entity = new FollowFailureDetailEntity();
                        entity.setPlatformType(f.getAccountType());
                        entity.setServer(f.getPlatform());
                        entity.setNode(f.getServerNode());
                        entity.setAccount(f.getAccount());
                        if(result.getMsg().contains("DataIntegrityViolationException")){
                            entity.setRemark("从库异常");
                        }
                        if(result.getMsg().length()>100){
                            entity.setRemark("系统错误");
                        }else{
                            entity.setRemark(result.getMsg());
                        }
                        entity.setRemark(result.getMsg());
                        entity.setRecordId(followUploadTraderUserEntity.getId());
                        entity.setType(TraderUserTypeEnum.ATTACH_VPS.getType());
                        followFailureDetailService.save(entity);
                     //   errList.add(entity);
                        //查找这个账号有没有
                        List<FollowTraderEntity> traders = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, f.getAccount()).eq(FollowTraderEntity::getPlatformId, f.getPlatformId()));
                        if(ObjectUtil.isEmpty(traders)){
                            //   f.setStatus(CloseOrOpenEnum.CLOSE.getValue());
                            error.add(f.getId());
                        }
                    }
                    countDownLatch.countDown();
                });
                f.setStatus(CloseOrOpenEnum.OPEN.getValue());
            });
            try {
                //等待
                countDownLatch.await();
              /*  if(ObjectUtil.isNotEmpty(errList)){
                    followFailureDetailService.saveBatch(errList);
                }*/
                int success = Long.valueOf(followUploadTraderUserEntity.getSuccessCount()).intValue()+ sum.get();
                followUploadTraderUserEntity.setFailureCount(Long.valueOf(err.get()));
                followUploadTraderUserEntity.setSuccessCount(Long.valueOf(success));
                followUploadTraderUserEntity.setStatus(TraderUserEnum.SUCCESS.getType());
                hangVpsVO.setTraderUserIds(traderUserIds);
                ObjectMapper objectMapper = new ObjectMapper();
                String json = null;
                try {
                    json = objectMapper.writeValueAsString(hangVpsVO);
                } catch (JsonProcessingException e) {
                    log.error("挂靠参数序列异常:"+e);
                }
                followUploadTraderUserEntity.setParams(json);
                followUploadTraderUserService.updateById(followUploadTraderUserEntity);
                updateBatchById(followTraderUserEntities);
                if(ObjectUtil.isNotEmpty(error)){
                    update(new  LambdaUpdateWrapper<FollowTraderUserEntity>().in(FollowTraderUserEntity::getId,error).set(FollowTraderUserEntity::getStatus,CloseOrOpenEnum.CLOSE.getValue()));
                }
            } catch (InterruptedException e) {
                log.error("挂靠失败"+e);
            }
        });

    }

    @Override
    public void addExcel(Long id, List<FollowTraderUserVO> followTraderUserVO) {
//        followFailureDetailService.list(new LambdaQueryWrapper<FollowFailureDetailEntity>().eq(FollowFailureDetailEntity::getRecordId,id));
//        if (ObjectUtil.isNotEmpty(followTraderUserVO)){
//            followFailureDetailService.remove(new LambdaQueryWrapper<FollowFailureDetailEntity>().eq(FollowFailureDetailEntity::getRecordId,id));
//        }
//        //成功
//        List<FollowTraderUserEntity> entityList = new ArrayList<>();
//        //失败
//        List<FollowFailureDetailEntity> failureList = new ArrayList<>();
//        for (FollowTraderUserVO vo : followTraderUserVO) {
//            String password = AesUtils.aesEncryptStr(vo.getPassword());
//            String account = vo.getAccount();
//            String accountType = vo.getAccountType().isEmpty() ? "MT4" : vo.getAccountType();
//            String platform = vo.getPlatform();
//            String node = vo.getServerNode();
//            String remark = vo.getRemark();
//            String sort = String.valueOf(vo.getSort()).isEmpty() ? "1" : String.valueOf(vo.getSort());
//            StringBuilder errorMsg = new StringBuilder();
//            if (!accountType.equals("MT4") && !accountType.equals("MT5")) {
//                errorMsg.append("账号类型必须是MT4或MT5; ");
//            }
////                    }else {
////                        if (accountType.equals("MT5")) {
////                            accountType = CloseOrOpenEnum.OPEN.getValue() + "";
////                        }
////                        if (accountType.equals("MT4")) {
////                            accountType = CloseOrOpenEnum.CLOSE.getValue() + "";
////                        }
////                    }
//
//            List<FollowTraderUserEntity> entities = list(new LambdaQueryWrapper<FollowTraderUserEntity>().eq(FollowTraderUserEntity::getAccount, account).eq(FollowTraderUserEntity::getPlatform, platform));
//            if (ObjectUtil.isNotEmpty(entities)) {
//                String errorRemark = "账号已存在;";
//                failureList.add(insertFailureDetail(account, accountType, platform, node, errorRemark, savedId));
//                failureCount++;
//                continue;
//            }
//
//            // 校验必填字段
////                    StringBuilder errorMsg = new StringBuilder();
//            if (account.isEmpty()) {
//                errorMsg.append("账号不能为空; ");
//            }else if (!NumberUtils.isDigits(account)){
//                errorMsg.append("账号需为数字; ");
//            }
////                }else {
////                    if (followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, account)).size() > 0) {
////                        errorMsg.append("账号已存在; ");
////                    }
////                }
//            if (password.isEmpty()) {
//                errorMsg.append("密码不能为空; ");
//            } else if (record.get(1).length() < 6 || record.get(1).length() > 16) {
//                errorMsg.append("密码需在6 ~ 16位之间; ");
//            }
//            if (platform.isEmpty()) {
//                errorMsg.append("服务器不能为空; ");
//            } else {
//                if (followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, platform)).size() == 0) {
//                    errorMsg.append("服务器不存在; ");
//                }
//            }
////                    if (!accountType.equals("0") && !accountType.equals("1")) {
////                        errorMsg.append("账号类型必须是MT4或MT5; ");
////                    }
//            if (ObjectUtil.isNotEmpty(node) && ObjectUtil.isNotEmpty(platform)) {
//                //将node拆分
//                String[] split = node.split(":");
//                if (split.length != 2) {
//                    errorMsg.append("节点格式不正确; ");
//                } else if (followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>()
//                        .eq(FollowBrokeServerEntity::getServerName, platform)
//                        .eq(FollowBrokeServerEntity::getServerNode, split[0])
//                        .eq(FollowBrokeServerEntity::getServerPort, split[1])).size() == 0) {
//                    errorMsg.append("节点不存在; ");
//                }
//            }
//            if (ObjectUtil.isEmpty(node) && ObjectUtil.isNotEmpty(platform)){
//                //根据platform 查询默认节点
//                List<FollowTestDetailVO> list = vos.stream().filter(vo -> vo.getServerName().equals(platform)).toList();
//                if (ObjectUtil.isNotEmpty(list)){
//                    List<FollowTestDetailVO> list1 = list.stream().filter(vo -> vo.getIsDefaultServer() == 0).toList();
//                    if (ObjectUtil.isNotEmpty(list1)){
//                        node = list1.get(0).getServerNode();
//                    }else {
//                        node = list.get(0).getServerNode();
//                    }
//                }
//            }
//
//            // 生成备注信息
//            String errorRemark = errorMsg.length() > 0 ? errorMsg.toString() : remark;
//            // 如果有错误，设置 upload_status 为 0
////                int uploadStatus = errorMsg.length() > 0 ? 0 : 1;
//            if (errorMsg.length() == 0) {
//                if (accountType.equals("MT5")) {
//                    accountType = CloseOrOpenEnum.OPEN.getValue() + "";
//                }
//                if (accountType.equals("MT4")) {
//                    accountType = CloseOrOpenEnum.CLOSE.getValue() + "";
//                }
//                entityList.add(insertAccount(account, password, accountType, platform, node, errorRemark, sort));
//                successCount++;
//            } else {
//                failureList.add(insertFailureDetail(account, accountType, platform, node, errorRemark, savedId));
//                failureCount++;
//            }
//        }
//        if (ObjectUtil.isNotEmpty(entityList)) {
//            //批量保存前还要检查一次并发重复的数据，platform和account同时相等的情况
//            entityList = entityList.stream()
//                    .collect(Collectors.toMap(
//                            entity -> entity.getAccount() + "_" + entity.getPlatform(),
//                            entity -> entity,
//                            (existing, replacement) -> existing))
//                    .values()
//                    .stream()
//                    .collect(Collectors.toList());  // 最终转换成 list
//            this.saveBatch(entityList);
//        }
//        if (ObjectUtil.isNotEmpty(failureList)) {
//            followFailureDetailService.saveBatch(failureList);
//        }
//        ObjectMapper objectMapper = new ObjectMapper();
//        String json = null;
//        try {
//            json = objectMapper.writeValueAsString(failureList);
//        } catch (JsonProcessingException e) {
//            log.error("挂靠参数序列异常:"+e);
//        }
//        LambdaUpdateWrapper<FollowUploadTraderUserEntity> updateWrapper = new LambdaUpdateWrapper<>();
//        updateWrapper.set(FollowUploadTraderUserEntity::getSuccessCount, successCount)
//                .set(FollowUploadTraderUserEntity::getFailureCount, failureCount)
//                .set(FollowUploadTraderUserEntity::getUploadTotal, failureCount + successCount)
//                .set(FollowUploadTraderUserEntity::getStatus, TraderUserEnum.SUCCESS.getType())
//                .set(FollowUploadTraderUserEntity::getParams,json)
//                .eq(FollowUploadTraderUserEntity::getId, savedId);
//        followUploadTraderUserService.update(updateWrapper);
//    }catch (Exception e) {
//        log.error("处理Excel文件时发生错误: ", e);
//    }
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

        Map<String,List<Long>> map=new HashMap<>();

        followTraderUsers.forEach(traderUser->{
            List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, traderUser.getAccount()).eq(FollowTraderEntity::getPlatformId, traderUser.getPlatformId()));
            AtomicReference<Boolean> flag= new AtomicReference<>(false);
            list.forEach(t->{
                if(!t.getType().equals(TraderTypeEnum.MASTER_REAL.getType())){
                    List<Long> idList = map.get(t.getIpAddr());
                    if(idList==null){
                        idList=new ArrayList<>();
                    }
                    idList.add(t.getId());
                    map.put(t.getIpAddr(),idList);
                }else {
                    flag.set(true);
                }
            });
            if(flag.get()){
                traderUser.setStatus(CloseOrOpenEnum.OPEN.getValue());
            }

        });
        HttpHeaders headerApplicationJsonAndToken = RestUtil.getHeaderApplicationJsonAndToken(request);
        if(ObjectUtil.isNotEmpty(map)){
            map.forEach((k,v)->{
                ThreadPoolUtils.getExecutor().execute(() -> {
                    Result result = RestUtil.sendRequest(request, k, HttpMethod.DELETE, FollowConstant.DEL_TRADER, v, headerApplicationJsonAndToken);
                });
            });
        }
        updateBatchById(followTraderUsers);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void modify(List<FollowTraderUserVO> vos) {
        for (FollowTraderUserVO vo : vos) {
            FollowGroupVO group = followGroupService.get(Long.valueOf(vo.getGroupId()));
            if (ObjectUtil.isEmpty(group)) {
                throw new ServerException("分组不存在");
            }
                LambdaUpdateWrapper<FollowTraderUserEntity> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(FollowTraderUserEntity::getId, vo.getId())
                        .set(FollowTraderUserEntity::getGroupName, group.getName())
                        .set(FollowTraderUserEntity::getGroupId, vo.getGroupId());
                baseMapper.update(updateWrapper);
        }
    }

    @Override
    public List<FollowTraderCountVO> getServerNodeCounts() {
        return baseMapper.getServerNodeCounts();
    }

    @Override
    public String getAccountCount(String serverName) {
        long count = this.count(new LambdaQueryWrapper<FollowTraderUserEntity>()
                .eq(FollowTraderUserEntity::getPlatform, serverName));
        return String.valueOf(count);
    }

    @Override
    public FollowTraderUserEntity getByAccount(String account) {
        List<FollowTraderUserEntity> list = list(new LambdaQueryWrapper<FollowTraderUserEntity>().eq(FollowTraderUserEntity::getAccount, account));
        if (ObjectUtil.isNotEmpty(list)) {
            return list.get(0);
        }
        return null;
    }
}