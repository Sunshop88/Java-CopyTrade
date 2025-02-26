package net.maku.followcom.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTraderUserConvert;
import net.maku.followcom.dao.FollowTraderUserDao;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.TraderUserEnum;
import net.maku.followcom.enums.TraderUserTypeEnum;
import net.maku.followcom.query.FollowTraderUserQuery;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.vo.FollowTraderUserExcelVO;
import net.maku.followcom.vo.FollowTraderUserVO;
import net.maku.followcom.vo.FollowUploadTraderUserVO;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.security.user.SecurityUser;
import online.mtapi.mt4.QuoteClient;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final FollowFailureDetailServiceImpl followFailureDetailServiceImpl;

    @Override
    public PageResult<FollowTraderUserVO> page(FollowTraderUserQuery query) {
        IPage<FollowTraderUserEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowTraderUserConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowTraderUserEntity> getWrapper(FollowTraderUserQuery query){
        LambdaQueryWrapper<FollowTraderUserEntity> wrapper = Wrappers.lambdaQuery();
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
        FollowTraderUserEntity entity = FollowTraderUserConvert.INSTANCE.convert(vo);
        FollowPlatformEntity first = followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, vo.getPlatform())).getFirst();
        if (ObjectUtil.isNotEmpty(first)){
            entity.setPlatformId(Math.toIntExact(first.getId()));
        }

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowTraderUserVO vo) {
        FollowTraderUserEntity entity = FollowTraderUserConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

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
            if (!records.isEmpty()) {
                CSVRecord firstRecord = records.get(0);

                // 提取数据
                String account = firstRecord.get("账号");
                String password = firstRecord.get("密码");
                String accountType = firstRecord.get("账号类型");
                String server = firstRecord.get("服务器");
                String node = firstRecord.get("节点");
                String remark = firstRecord.get("备注");

                // 写入到输出流
                csvPrinter.printRecord(account, password, accountType, server, node, remark);
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
                String password = record.get(1);
                String accountType = record.get(2).isEmpty() ? "MT4" : record.get(2).toUpperCase();
                String platform = record.get(3);
                String node = record.get(4);
                String remark = record.get(5);

                // 校验必填字段
                StringBuilder errorMsg = new StringBuilder();
                if (account.isEmpty()) {
                    errorMsg.append("账号不能为空; ");
                }
                if (password.isEmpty()) {
                    errorMsg.append("密码不能为空; ");
                }
                if (platform.isEmpty()) {
                    errorMsg.append("服务器不能为空; ");
                }
                if (!accountType.equals("MT4") && !accountType.equals("MT5")) {
                    errorMsg.append("账号类型必须是MT4或MT5; ");
                }

                // 生成备注信息
                String errorRemark = errorMsg.length() > 0 ? errorMsg.toString() : remark;
                // 如果有错误，设置 upload_status 为 0
//                int uploadStatus = errorMsg.length() > 0 ? 0 : 1;
                if (errorMsg.length() == 0) {
                    entityList.add(insertAccount(account, password, accountType, platform, node, errorRemark));
                    successCount++;
                } else {
                    failureList.add(insertFailureDetail(account, accountType, platform, node, errorRemark,savedId));
                    failureCount++;
                }
            }
            this.saveBatch(entityList);
            followFailureDetailServiceImpl.saveBatch(failureList);
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
        entity.setRecordId(Math.toIntExact(savedId));
        entity.setType(TraderUserTypeEnum.ADD_ACCOUNT.getType());
        return entity;
    }

    // 插入账号
    private FollowTraderUserEntity insertAccount(String account, String password, String accountType, String platform, String node, String errorRemark) {
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
        entity.setServerNode(node);
        entity.setRemark(errorRemark);
        return entity;
    }

    @Override
    public void updateGroup(List<Long> idList, String group) {
        LambdaQueryWrapper<FollowGroupEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FollowGroupEntity::getName, group);
        long groupId = followGroupService.list(queryWrapper).getFirst().getId();
        for (Long id : idList) {
            LambdaUpdateWrapper<FollowTraderUserEntity> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(FollowTraderUserEntity::getId, id)
                    .set(FollowTraderUserEntity::getGroupName, group)
                    .set(FollowTraderUserEntity::getGroupId, groupId);
            baseMapper.update(updateWrapper);
        }
    }

    @Override
    public void updatePasswords(List<FollowTraderUserVO> voList, String password, String confirmPassword, HttpServletRequest req) {
        if (!password.equals(confirmPassword)) {
            throw new ServerException("两次密码输入不一致");
        }

        // 设置状态
        FollowUploadTraderUserVO followUploadTraderUserVO = new FollowUploadTraderUserVO();
        followUploadTraderUserVO.setStatus(TraderUserEnum.IN_PROGRESS.getType());
        followUploadTraderUserVO.setOperator(SecurityUser.getUser().getUsername());
        followUploadTraderUserVO.setUploadTime(LocalDateTime.now());
        followUploadTraderUserService.save(followUploadTraderUserVO);

        long uploadTotal = voList.size();
        long successCount = 0;
        long failureCount = 0;

        // 提前在主线程中获取 Token 和其他需要的头信息
        String token = req.getHeader("Authorization");
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", token);
        headers.put("Content-Type", "application/json");

        // 根据account去follow_trader查询status筛选出正常还是异常
        for (FollowTraderUserVO vo : voList) {
            // 查询账号状态
            LambdaQueryWrapper<FollowTraderEntity> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(FollowTraderEntity::getAccount, vo.getAccount());
            FollowTraderEntity followTraderEntity = followTraderService.list(queryWrapper).getLast();
            String remark ="";
            if (ObjectUtil.isNotEmpty(followTraderEntity)) {
                if (followTraderEntity.getStatus() == 0) {
                    // 账号正常登录
                    followTraderEntity.setPassword(password);
                    followTraderService.updateById(followTraderEntity);

                    QuoteClient quoteClient = null;
                    try {
                        // 修改 MT4 密码
                        quoteClient.ChangePassword(vo.getPassword(), false);
                    } catch (IOException e) {
                        remark = "MT4修改密码异常, 检查参数" + "密码：" + vo.getPassword() + "是否投资密码" + false + ", 异常原因" + e;
                    } catch (online.mtapi.mt4.Exception.ServerException e) {
                        remark = "MT4修改密码异常, 检查参数" + "密码：" + vo.getPassword() + "是否投资密码" + false + ", 异常原因" + e;
                    }

                    String url = MessageFormat.format("http://{0}:{1}{2}", followTraderEntity.getIpAddr(), FollowConstant.VPS_PORT, FollowConstant.VPS_RECONNECTION_Trader);
                    RestTemplate restTemplate = new RestTemplate();

                    // 使用提前提取的 headers 构建请求头
                    HttpHeaders httpHeaders = new HttpHeaders();
                    httpHeaders.setAll(headers);  // 注入提前获取的请求头
                    HttpEntity<String> entity = new HttpEntity<>(followTraderEntity.getId().toString(), httpHeaders);

                    ResponseEntity<JSONObject> response = restTemplate.exchange(url, HttpMethod.POST, entity, JSONObject.class);
                    if (response.getBody() != null && !response.getBody().getString("msg").equals("success")) {
                        log.error("账号重连失败: " + followTraderEntity.getIpAddr());
                        remark += "重连失败; ";
                    }

                    // 更新traderUser密码并记录备注
                    LambdaUpdateWrapper<FollowTraderUserEntity> updateWrapper = new LambdaUpdateWrapper<>();
                    updateWrapper.eq(FollowTraderUserEntity::getId, vo.getId())
                            .set(FollowTraderUserEntity::getPassword, password)
                            ;

                    // 仅在有备注时设置备注
                    if (!remark.isEmpty()) {
                        updateWrapper.set(FollowTraderUserEntity::getRemark, remark);
                        failureCount++; // 记录失败
                    } else {
                        updateWrapper.set(FollowTraderUserEntity::getRemark, vo.getRemark());
                        successCount++; // 如果没有备注，算作成功
                    }
                    baseMapper.update(updateWrapper);
                }
            }
        }

        followUploadTraderUserVO.setUploadTotal(uploadTotal);
        followUploadTraderUserVO.setSuccessCount(successCount);
        followUploadTraderUserVO.setFailureCount(failureCount);
        followUploadTraderUserVO.setStatus(TraderUserEnum.SUCCESS.getType());
        followUploadTraderUserService.update(followUploadTraderUserVO);
    }

    @Override
    public void updatePassword(FollowTraderUserVO vo, HttpServletRequest req) {
        if (!vo.getPassword().equals(vo.getConfirmPassword())) {
            throw new ServerException("两次密码输入不一致");
        }
        LambdaQueryWrapper<FollowTraderEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FollowTraderEntity::getAccount, vo.getAccount());
        FollowTraderEntity followTraderEntity = followTraderService.list(queryWrapper).getLast();
        if (ObjectUtil.isNotEmpty(followTraderEntity) && followTraderEntity.getStatus() == 0) {
            String token = req.getHeader("Authorization");
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", token);
            headers.put("Content-Type", "application/json");
            // 账号正常登录
            followTraderEntity.setPassword(vo.getPassword());
            followTraderService.updateById(followTraderEntity);

            QuoteClient quoteClient = null;
            try {
                // 修改 MT4 密码
                quoteClient.ChangePassword(vo.getPassword(), false);
            } catch (IOException e) {
                log.error("MT4修改密码异常, 检查参数" + "密码：" + vo.getPassword() + "是否投资密码" + false + ", 异常原因" + e);
            } catch (online.mtapi.mt4.Exception.ServerException e) {
                log.error("MT4修改密码异常, 检查参数" + "密码：" + vo.getPassword() + "是否投资密码" + false + ", 异常原因" + e);
            }

            String url = MessageFormat.format("http://{0}:{1}{2}", followTraderEntity.getIpAddr(), FollowConstant.VPS_PORT, FollowConstant.VPS_RECONNECTION_Trader);
            RestTemplate restTemplate = new RestTemplate();

            // 使用提前提取的 headers 构建请求头
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setAll(headers);  // 注入提前获取的请求头
            HttpEntity<String> entity = new HttpEntity<>(followTraderEntity.getId().toString(), httpHeaders);

            ResponseEntity<JSONObject> response = restTemplate.exchange(url, HttpMethod.POST, entity, JSONObject.class);
            if (response.getBody() != null && !response.getBody().getString("msg").equals("success")) {
                log.error("账号重连失败: " + followTraderEntity.getAccount());
            }
        }
        vo.setPassword(vo.getPassword());
        this.update(vo);
    }

}