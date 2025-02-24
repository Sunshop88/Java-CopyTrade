package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTraderUserConvert;
import net.maku.followcom.dao.FollowTraderUserDao;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowTraderUserEntity;
import net.maku.followcom.query.FollowTraderUserQuery;
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.service.FollowTraderUserService;
import net.maku.followcom.vo.FollowTraderUserExcelVO;
import net.maku.followcom.vo.FollowTraderUserVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.excel.ExcelFinishCallBack;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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

    @Override
    public PageResult<FollowTraderUserVO> page(FollowTraderUserQuery query) {
        IPage<FollowTraderUserEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowTraderUserConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowTraderUserEntity> getWrapper(FollowTraderUserQuery query){
        LambdaQueryWrapper<FollowTraderUserEntity> wrapper = Wrappers.lambdaQuery();
        if (ObjectUtil.isNotEmpty(query.getUploadId())){
            wrapper.eq(FollowTraderUserEntity::getUploadId,query.getUploadId());
        }
        if (ObjectUtil.isNotEmpty(query.getUploadStatus())){
            wrapper.eq(FollowTraderUserEntity::getUploadStatus,query.getUploadStatus());
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
        List<FollowTraderUserEntity> entityList = new ArrayList<>();
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
                // 如果有错误，设置 upload_status 为 1
                int uploadStatus = errorMsg.length() > 0 ? 1 : 0;
                entityList.add(insertAccount(account, password, accountType, platform, node, errorRemark, uploadStatus,savedId));

            }
            saveBatch(entityList);
        } catch (Exception e) {
            log.error("处理Excel文件时发生错误: ", e);
        }
    }


    private FollowTraderUserEntity insertAccount(String account, String password, String accountType, String platform, String node, String errorRemark, int uploadStatus, Long savedId) {
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
        entity.setUploadStatus(uploadStatus);
        entity.setUploadId(Math.toIntExact(savedId));
        return entity;
    }

}