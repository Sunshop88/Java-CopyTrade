package net.maku.followcom.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.service.FollowTraderService;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.followcom.convert.FollowVarietyConvert;
import net.maku.followcom.entity.FollowVarietyEntity;
import net.maku.followcom.query.FollowVarietyQuery;
import net.maku.followcom.vo.FollowPlatformVO;
import net.maku.followcom.vo.FollowVarietyVO;
import net.maku.followcom.dao.FollowVarietyDao;
import net.maku.followcom.service.FollowVarietyService;
import com.fhs.trans.service.impl.TransService;
import net.maku.followcom.vo.FollowVarietyExcelVO;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;


/**
 * 品种匹配
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
@Slf4j
public class FollowVarietyServiceImpl extends BaseServiceImpl<FollowVarietyDao, FollowVarietyEntity> implements FollowVarietyService {
    private final TransService transService;
    private final ResourceLoader resourceLoader;
    private final FollowPlatformServiceImpl followPlatformServiceImpl;
    private final FollowTraderService followTraderService;

    @Override
    public PageResult<FollowVarietyVO> page(FollowVarietyQuery query) {
        IPage<FollowVarietyEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowVarietyConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }

    private LambdaQueryWrapper<FollowVarietyEntity> getWrapper(FollowVarietyQuery query) {
        LambdaQueryWrapper<FollowVarietyEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.like(StrUtil.isNotBlank(query.getStdSymbol()), FollowVarietyEntity::getStdSymbol, query.getStdSymbol());
        return wrapper;
    }

    @Override
    public FollowVarietyVO get(Long id) {
        FollowVarietyEntity entity = baseMapper.selectById(id);
        FollowVarietyVO vo = FollowVarietyConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowVarietyVO vo) {
        FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowVarietyVO vo) {
        FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(vo);

        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    @CacheEvict(
            value = "followVarietyCache", // 缓存名称
            key = "#template"
    )
    public void importByExcel(MultipartFile file, Integer template, String templateName) throws Exception {
        String fileName = file.getOriginalFilename();
        if (fileName != null && fileName.toLowerCase().endsWith(".csv")) {
            // 处理CSV文件
            importCsv(file, template, templateName);
        } else if (fileName != null && (fileName.toLowerCase().endsWith(".xls") || fileName.toLowerCase().endsWith(".xlsx"))) {
            // 处理Excel文件
            importExcel(file, template, templateName);
        } else {
            throw new Exception("Unsupported file type. Please upload a CSV or Excel file.");
        }
    }

    @Override
    public List<FollowVarietyVO> getListByTemplate() {
        List<FollowVarietyEntity> list = list(new LambdaQueryWrapper<FollowVarietyEntity>()
                .select(FollowVarietyEntity::getTemplateId, FollowVarietyEntity::getTemplateName)
                .groupBy(FollowVarietyEntity::getTemplateId, FollowVarietyEntity::getTemplateName)
                .orderByAsc(FollowVarietyEntity::getTemplateId));

        return FollowVarietyConvert.INSTANCE.convertList(list);
    }

    @Override
    public void updateTemplateName(Integer template, String templateName) {
        UpdateWrapper<FollowVarietyEntity> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("template_id", template);
        updateWrapper.set("template_name", templateName);
        baseMapper.update(updateWrapper);
    }

    @Override
    public List<FollowVarietyVO> listSymbol() {
        //查询所有的品种名称，不能重复
        List<FollowVarietyEntity> list = baseMapper.selectList(new LambdaQueryWrapper<FollowVarietyEntity>()
                .select(FollowVarietyEntity::getStdSymbol)
                .groupBy(FollowVarietyEntity::getStdSymbol));
        return FollowVarietyConvert.INSTANCE.convertList(list);
    }

    @Override
    public boolean deleteTemplate(List<Integer> idList) {
        if (idList.isEmpty()) {
            return false;
        }
        //根据templateId删除数据
        baseMapper.delete(new LambdaQueryWrapper<FollowVarietyEntity>().in(FollowVarietyEntity::getTemplateId, idList));
        return true;
    }

    @Override
    public int getBeginTemplateId() {
        //数据库中模板id全为空抛异常
        if (baseMapper.selectCount(new LambdaQueryWrapper<FollowVarietyEntity>().isNull(FollowVarietyEntity::getTemplateId)).equals(baseMapper.selectCount(new LambdaQueryWrapper<FollowVarietyEntity>()))) {
            throw new ServerException("模板id不能为空");
        }
        return baseMapper.selectOne(new LambdaQueryWrapper<FollowVarietyEntity>().orderByAsc(FollowVarietyEntity::getTemplateId).last("limit 1")).getTemplateId();
    }

    @Override
    public boolean checkTemplate(List<Integer> idList) {
        //策略者或者跟单者绑定了该模板就不能删除
        for (Integer id : idList) {
            if (followTraderService.exists(new LambdaQueryWrapper<FollowTraderEntity>()
                    .eq(FollowTraderEntity::getTemplateId, id))){
               throw new ServerException("策略者或者跟单者绑定了该模板不能删除");
            }
        }
        return true;
    }

    @Override
    @CacheEvict(
            value = "followVarietyCache", // 缓存名称
            key = "#template"
    )
    public boolean updateCache(Integer template) {
        return true;
    }

    @Override
    public boolean updateSymbol(List<FollowVarietyVO> followVarietyVO) {
        List<FollowVarietyEntity> list = FollowVarietyConvert.INSTANCE.convertList2(followVarietyVO);
        for (FollowVarietyEntity entity : list){
            LambdaUpdateWrapper<FollowVarietyEntity> wrapper = new LambdaUpdateWrapper<>();
            wrapper.set(ObjectUtil.isNotEmpty(entity.getStdContract()),FollowVarietyEntity::getStdSymbol,entity.getStdContract());
            wrapper.set(ObjectUtil.isNotEmpty(entity.getBrokerSymbol()),FollowVarietyEntity::getStdSymbol,entity.getBrokerSymbol());
            wrapper.eq(FollowVarietyEntity::getId,entity.getId());
            baseMapper.update(entity,wrapper);
        }
        return true;
    }

    @Override
    @Cacheable(
            value = "followVarietyCache", // 缓存名称
            key = "#templateId",          // 缓存键
            unless = "#result == null || #result.isEmpty()" // 空结果不缓存
    )
    public List<FollowVarietyEntity> getListByTemplated(Integer templateId) {
        log.info("未进入缓存");
        return this.list(new LambdaQueryWrapper<FollowVarietyEntity>().eq(FollowVarietyEntity::getTemplateId, templateId));
    }

    public void importCsv(MultipartFile file, Integer template, String templateName) throws IOException {
        try {
            InputStreamReader reader = new InputStreamReader(file.getInputStream(), Charset.forName("GBK"));
            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader());
            List<String> brokerNames = new ArrayList<>(csvParser.getHeaderMap().keySet());
            for (CSVRecord record : csvParser) {
                String stdContractStr = record.get(0); // 第一个字段可能是 stdContract
                String stdSymbol = record.get(1);      // 第二个字段是 stdSymbol
                Integer stdContract = null;
                if (ObjectUtil.isEmpty(stdSymbol)) continue;
                // 解析 stdContract 字段
                if (!ObjectUtil.isEmpty(stdContractStr)) {
                    try {
                        stdContract = Integer.valueOf(stdContractStr.trim());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid stdContract value: " + stdContractStr);
                    }
                }
                // 根据template更新所有具有相同 stdSymbol 的记录的 stdContract 值
                LambdaQueryWrapper<FollowVarietyEntity> wrapper = Wrappers.lambdaQuery();
                wrapper.eq(FollowVarietyEntity::getTemplateId, template)
                        .eq(FollowVarietyEntity::getStdSymbol, stdSymbol);
                // 根据 template 查询数据库中的数据
                List<FollowVarietyEntity> existingRecords = baseMapper.selectList(wrapper);
                log.info("template: {}", template);
                log.info("stdSymbol: {}", stdSymbol);
                log.info("existingRecords size: {}", existingRecords.size());
                for (FollowVarietyEntity recordToUpdate : existingRecords) {
                    log.info("Updating record: {}", recordToUpdate);
                    recordToUpdate.setStdContract(stdContract);
                    int updateResult = baseMapper.updateById(recordToUpdate);
                    log.info("Update result: {}", updateResult);
                }
                // 遍历 brokerName 列，处理 brokerSymbol 和 brokerName
                int headerSize = brokerNames.size();
                for (int i = 2; i < Math.min(headerSize, record.size()); i++) { // 添加边界检查
                    String brokerName = brokerNames.get(i);
                    String brokerSymbol = record.get(i);
                    // 删除已有的相同 stdSymbol 和 brokerName 对应的 brokerSymbol，即便 brokerSymbol 为 null
                    LambdaQueryWrapper<FollowVarietyEntity> deleteQueryWrapper = Wrappers.lambdaQuery();
                    deleteQueryWrapper.eq(FollowVarietyEntity::getStdSymbol, stdSymbol)
                            .eq(FollowVarietyEntity::getBrokerName, brokerName.trim())
                            .eq(FollowVarietyEntity::getTemplateId, template);
                    baseMapper.delete(deleteQueryWrapper);

                    FollowVarietyVO brokerData = new FollowVarietyVO();
                    brokerData.setStdContract(stdContract);
                    brokerData.setStdSymbol(stdSymbol);
                    brokerData.setBrokerName(brokerName.trim());
                    brokerData.setTemplateId(template); // 设置 template 字段

                    if (ObjectUtil.isEmpty(brokerSymbol)) {
                        // brokerSymbol 为空的情况
                        brokerData.setBrokerSymbol(null);
                        FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
                        baseMapper.insert(entity);
                    } else {
                        // brokerSymbol 不为空的情况，可能包含多个符号
                        String[] brokerSymbolParts = brokerSymbol.split("/");
                        for (String symbol : brokerSymbolParts) {
                            brokerData.setBrokerSymbol(symbol.trim());
                            FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
                            try {
                                baseMapper.insert(entity);
                            } catch (Exception e) {
                                log.info("插入失败: " + brokerData.getBrokerName() + "-" + brokerData.getBrokerSymbol());
                            }
                        }
                    }
                }
                // 只有 stdSymbol 和 stdContract，没有 brokerName 和 brokerSymbol时保存
                if (record.size() == 2 || (stdContract != null && record.size() == 3 && brokerNames.size() < 3)) {
                    FollowVarietyVO brokerData = new FollowVarietyVO();
                    brokerData.setStdSymbol(stdSymbol);
                    brokerData.setStdContract(stdContract);
                    brokerData.setBrokerName(null);
                    brokerData.setBrokerSymbol(null);
                    brokerData.setTemplateId(template); // 设置 template 字段
                    FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
                    baseMapper.insert(entity);
                }
            }
            if (!ObjectUtil.isEmpty(templateName)) {
                //根据template更新templateName
                LambdaUpdateWrapper<FollowVarietyEntity> updateWrapper = Wrappers.lambdaUpdate();
                updateWrapper.eq(FollowVarietyEntity::getTemplateId, template)
                        .set(FollowVarietyEntity::getTemplateName, templateName);
                baseMapper.update(updateWrapper);
            }
        } catch (Exception e) {
            log.error("导入失败", e);
        }
    }

//    public void importCsv(MultipartFile file, Integer template, String templateName) throws IOException {
//        try {
//            InputStreamReader reader = new InputStreamReader(file.getInputStream(), Charset.forName("GBK"));
//            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader());
//            List<String> brokerNames = new ArrayList<>(csvParser.getHeaderMap().keySet());
//            for (CSVRecord record : csvParser) {
//                String stdContractStr = record.get(0); // 第一个字段可能是 stdContract
//                String stdSymbol = record.get(1);      // 第二个字段是 stdSymbol
//                Integer stdContract = null;
//                if (ObjectUtil.isEmpty(stdSymbol)) continue;
//                // 解析 stdContract 字段
//                if (!ObjectUtil.isEmpty(stdContractStr)) {
//                    try {
//                        stdContract = Integer.valueOf(stdContractStr.trim());
//                    } catch (NumberFormatException e) {
//                        log.warn("Invalid stdContract value: " + stdContractStr);
//                    }
//                }
//                // 根据template更新所有具有相同 stdSymbol 的记录的 stdContract 值
//                LambdaQueryWrapper<FollowVarietyEntity> wrapper = Wrappers.lambdaQuery();
//                wrapper.eq(FollowVarietyEntity::getTemplateId, template)
//                        .eq(FollowVarietyEntity::getStdSymbol, stdSymbol);
//                // 根据 template 查询数据库中的数据
//                List<FollowVarietyEntity> existingRecords = baseMapper.selectList(wrapper);
//                log.info("template: {}", template);
//                log.info("stdSymbol: {}", stdSymbol);
//                log.info("existingRecords size: {}", existingRecords.size());
//                for (FollowVarietyEntity recordToUpdate : existingRecords) {
//                    log.info("Updating record: {}", recordToUpdate);
//                    recordToUpdate.setStdContract(stdContract);
////                    baseMapper.updateById(recordToUpdate);
//                    int updateResult = baseMapper.updateById(recordToUpdate);
//
//                    log.info("Update result: {}", updateResult);
//
//                }
//                // 遍历 brokerName 列，处理 brokerSymbol 和 brokerName
//                for (int i = 2; i < record.size(); i++) {
//                    String brokerName = brokerNames.get(i);
//                    String brokerSymbol = record.get(i);
//                    // 删除已有的相同 stdSymbol 和 brokerName 对应的 brokerSymbol，即便 brokerSymbol 为 null
//                    LambdaQueryWrapper<FollowVarietyEntity> deleteQueryWrapper = Wrappers.lambdaQuery();
//                    deleteQueryWrapper.eq(FollowVarietyEntity::getStdSymbol, stdSymbol)
//                            .eq(FollowVarietyEntity::getBrokerName, brokerName.trim())
//                            .eq(FollowVarietyEntity::getTemplateId, template);
//                    baseMapper.delete(deleteQueryWrapper);
//
//                    FollowVarietyVO brokerData = new FollowVarietyVO();
//                    brokerData.setStdContract(stdContract);
//                    brokerData.setStdSymbol(stdSymbol);
//                    brokerData.setBrokerName(brokerName.trim());
//                    brokerData.setTemplateId(template); // 设置 template 字段
//
//                    if (ObjectUtil.isEmpty(brokerSymbol)) {
//                        // brokerSymbol 为空的情况
//                        brokerData.setBrokerSymbol(null);
//                        FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
//                        baseMapper.insert(entity);
//                    } else {
//                        // brokerSymbol 不为空的情况，可能包含多个符号
//                        String[] brokerSymbolParts = brokerSymbol.split("/");
//                        for (String symbol : brokerSymbolParts) {
//                            brokerData.setBrokerSymbol(symbol.trim());
//                            FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
//                            try {
//                                baseMapper.insert(entity);
//                            } catch (Exception e) {
//                                log.info("插入失败: " + brokerData.getBrokerName() + "-" + brokerData.getBrokerSymbol());
//                            }
//                        }
//                    }
//                }
//                // 只有 stdSymbol 和 stdContract，没有 brokerName 和 brokerSymbol时保存
//                if (record.size() == 2 || (stdContract != null && record.size() == 3 && brokerNames.size() < 3)) {
//                    FollowVarietyVO brokerData = new FollowVarietyVO();
//                    brokerData.setStdSymbol(stdSymbol);
//                    brokerData.setStdContract(stdContract);
//                    brokerData.setBrokerName(null);
//                    brokerData.setBrokerSymbol(null);
//                    brokerData.setTemplateId(template); // 设置 template 字段
//                    FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
//                    baseMapper.insert(entity);
//                }
////                // 根据template更新所有具有相同 stdSymbol 的记录的 stdContract 值
////                LambdaQueryWrapper<FollowVarietyEntity> wrapper = Wrappers.lambdaQuery();
////                wrapper.eq(FollowVarietyEntity::getTemplateId, template)
////                        .eq(FollowVarietyEntity::getStdSymbol, stdSymbol);
////                // 根据 template 查询数据库中的数据
////                List<FollowVarietyEntity> existingRecords = baseMapper.selectList(wrapper);
////                for (FollowVarietyEntity recordToUpdate : existingRecords) {
////                    recordToUpdate.setStdContract(stdContract);
////                    baseMapper.updateById(recordToUpdate);
////                }
//            }
//            if (!ObjectUtil.isEmpty(templateName)) {
//                //根据template更新templateName
//                LambdaUpdateWrapper<FollowVarietyEntity> updateWrapper = Wrappers.lambdaUpdate();
//                updateWrapper.eq(FollowVarietyEntity::getTemplateId, template)
//                        .set(FollowVarietyEntity::getTemplateName, templateName);
//                baseMapper.update(updateWrapper);
//            }
//        } catch (Exception e) {
//            log.error("导入失败", e);
//        }
//    }

    public void importExcel(MultipartFile file, Integer template, String templateName) throws IOException {
        try {
            InputStream inputStream = file.getInputStream();
            Workbook workbook = new XSSFWorkbook(inputStream);
            Sheet sheet = workbook.getSheetAt(0);
            // 获取表头
            Row headerRow = sheet.getRow(0);
            List<String> brokerNames = new ArrayList<>();
            for (Cell cell : headerRow) {
                brokerNames.add(cell.getStringCellValue());
            }
            // 从第二行开始遍历数据
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                String stdContractStr = getCellStringValue(row.getCell(0));
                String stdSymbol = getCellStringValue(row.getCell(1));
                Integer stdContract = null;
                if (stdSymbol == null || stdSymbol.isEmpty()) continue;

                // 解析 stdContract 字段
                if (stdContractStr != null && !stdContractStr.isEmpty()) {
                    try {
                        stdContract = Integer.parseInt(stdContractStr.trim());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid stdContract value: " + stdContractStr);
                    }
                }
//                // 根据template更新所有具有相同 stdSymbol 的记录的 stdContract 值
//                LambdaQueryWrapper<FollowVarietyEntity> wrapper = Wrappers.lambdaQuery();
//                wrapper.eq(FollowVarietyEntity::getTemplateId, template)
//                        .eq(FollowVarietyEntity::getStdSymbol, stdSymbol);
//                // 根据 template 查询数据库中的数据
//                List<FollowVarietyEntity> existingRecords = baseMapper.selectList(wrapper);
//                for (FollowVarietyEntity recordToUpdate : existingRecords) {
//                    recordToUpdate.setStdContract(stdContract);
//                    baseMapper.updateById(recordToUpdate);
//                }
                // 遍历 brokerName 列，处理 brokerSymbol 和 brokerName
                for (int i = 2; i < brokerNames.size(); i++) {
                    String brokerName = brokerNames.get(i);
                    String brokerSymbol = getCellStringValue(row.getCell(i));

                    LambdaQueryWrapper<FollowVarietyEntity> deleteQueryWrapper = Wrappers.lambdaQuery();
                    deleteQueryWrapper.eq(FollowVarietyEntity::getStdSymbol, stdSymbol)
                            .eq(FollowVarietyEntity::getBrokerName, brokerName.trim())
                            .eq(FollowVarietyEntity::getTemplateId, template); // 添加 template 条件
                    baseMapper.delete(deleteQueryWrapper);

                    FollowVarietyVO brokerData = new FollowVarietyVO();
                    brokerData.setStdContract(stdContract);
                    brokerData.setStdSymbol(stdSymbol);
                    brokerData.setBrokerName(brokerName.trim());
                    brokerData.setTemplateId(template); // 设置 template 字段

                    if (brokerSymbol == null || brokerSymbol.isEmpty()) {
                        // brokerSymbol 为空的情况
                        brokerData.setBrokerSymbol(null);
                        FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
                        baseMapper.insert(entity);
                    } else {
                        // brokerSymbol 不为空的情况，可能包含多个符号
                        String[] brokerSymbolParts = brokerSymbol.split("/");
                        for (String symbol : brokerSymbolParts) {
                            brokerData.setBrokerSymbol(symbol.trim());
                            FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
                            try {
                                baseMapper.insert(entity);
                            } catch (Exception e) {
                                log.info("插入失败: " + brokerData.getBrokerName() + "-" + brokerData.getBrokerSymbol());
                            }
                        }
                    }
                }
                // 如果只有 stdSymbol 和 stdContract，没有 brokerName 和 brokerSymbol，也要保存
                if (row.getLastCellNum() == 2 || (stdContract != null && row.getLastCellNum() == 3 && brokerNames.size() < 3)) {
                    FollowVarietyVO brokerData = new FollowVarietyVO();
                    brokerData.setStdSymbol(stdSymbol);
                    brokerData.setStdContract(stdContract);
                    brokerData.setBrokerName(null);
                    brokerData.setBrokerSymbol(null);
                    brokerData.setTemplateId(template); // 设置 template 字段

                    FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
                    // 插入或更新记录，确保 stdContract 和 stdSymbol 成功保存
                    LambdaQueryWrapper<FollowVarietyEntity> queryWrapper = Wrappers.lambdaQuery();
                    queryWrapper.eq(FollowVarietyEntity::getStdSymbol, stdSymbol)
                            .eq(FollowVarietyEntity::getTemplateId, template); // 添加 template 条件

                    // 如果存在则更新，否则插入
                    if (baseMapper.selectCount(queryWrapper) > 0) {
                        baseMapper.update(entity, queryWrapper);
                    } else {
                        baseMapper.insert(entity);
                    }
                }
                // 根据template更新所有具有相同 stdSymbol 的记录的 stdContract 值
                LambdaQueryWrapper<FollowVarietyEntity> wrapper = Wrappers.lambdaQuery();
                wrapper.eq(FollowVarietyEntity::getTemplateId, template)
                        .eq(FollowVarietyEntity::getStdSymbol, stdSymbol);
                // 根据 template 查询数据库中的数据
                List<FollowVarietyEntity> existingRecords = baseMapper.selectList(wrapper);
                for (FollowVarietyEntity recordToUpdate : existingRecords) {
                    recordToUpdate.setStdContract(stdContract);
                    baseMapper.updateById(recordToUpdate);
                }
            }
            if (!ObjectUtil.isEmpty(templateName)) {
                //根据template更新templateName
                LambdaUpdateWrapper<FollowVarietyEntity> updateWrapper = Wrappers.lambdaUpdate();
                updateWrapper.eq(FollowVarietyEntity::getTemplateId, template)
                        .set(FollowVarietyEntity::getTemplateName, templateName);
                baseMapper.update(updateWrapper);
            }
        } catch (Exception e) {
            throw new ServerException("无法读取文件");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void addByExcel(MultipartFile file, String templateName) throws Exception {
        String fileName = file.getOriginalFilename();
        LambdaQueryWrapper<FollowVarietyEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(FollowVarietyEntity::getTemplateId)
                .orderByDesc(FollowVarietyEntity::getTemplateId)
                .last("LIMIT 1");
        FollowVarietyEntity maxTemplateEntity = baseMapper.selectOne(queryWrapper);
        int template = (maxTemplateEntity != null && maxTemplateEntity.getTemplateId() != null)
                ? maxTemplateEntity.getTemplateId() + 1 : 1;

        if (fileName != null && fileName.toLowerCase().endsWith(".csv")) {
            // 处理CSV文件
            addCsv(file, template, templateName);
        } else if (fileName != null && (fileName.toLowerCase().endsWith(".xls") || fileName.toLowerCase().endsWith(".xlsx"))) {
            // 处理Excel文件
            addExcel(file, template, templateName);
        } else {
            throw new Exception("Unsupported file type. Please upload a CSV or Excel file.");
        }
    }

    public void addCsv(MultipartFile file, Integer template, String templateName) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), Charset.forName("GBK"));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            List<String> brokerNames = new ArrayList<>(csvParser.getHeaderMap().keySet());

            for (CSVRecord record : csvParser) {
                String stdContractStr = record.get(0); // 第一个字段可能是 stdContract
                String stdSymbol = record.get(1);      // 第二个字段是 stdSymbol
                Integer stdContract = null;
                if (ObjectUtil.isEmpty(stdSymbol)) continue;

                // 解析 stdContract 字段
                if (!ObjectUtil.isEmpty(stdContractStr)) {
                    try {
                        stdContract = Integer.valueOf(stdContractStr.trim());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid stdContract value: " + stdContractStr);
                    }
                }

                // 更新所有具有相同 stdSymbol 的记录的 stdContract 值
                baseMapper.updateStdContractByStdSymbol(stdSymbol, stdContract);

                // 遍历 brokerName 列，处理 brokerSymbol 和 brokerName
                int headerSize = brokerNames.size();
                for (int i = 2; i < Math.min(headerSize, record.size()); i++) { // 添加边界检查
                    String brokerName = brokerNames.get(i);
                    String brokerSymbol = record.get(i);

                    FollowVarietyVO brokerData = new FollowVarietyVO();
                    brokerData.setStdContract(stdContract);
                    brokerData.setStdSymbol(stdSymbol);
                    brokerData.setBrokerName(brokerName.trim());
                    brokerData.setTemplateId(template); // 设置 template 字段
                    brokerData.setTemplateName(templateName);

                    if (ObjectUtil.isEmpty(brokerSymbol)) {
                        // brokerSymbol 为空的情况
                        brokerData.setBrokerSymbol(null);
                        FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
                        baseMapper.insert(entity);
                    } else {
                        // brokerSymbol 不为空的情况，可能包含多个符号
                        String[] brokerSymbolParts = brokerSymbol.split("/");
                        for (String symbol : brokerSymbolParts) {
                            brokerData.setBrokerSymbol(symbol.trim());
                            FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
                            try {
                                baseMapper.insert(entity);
                            } catch (Exception e) {
                                log.info("插入失败: " + brokerData.getBrokerName() + "-" + brokerData.getBrokerSymbol());
                            }
                        }
                    }
                }

                // 只有 stdSymbol 和 stdContract，没有 brokerName 和 brokerSymbol时保存
                if (record.size() == 2 || (stdContract != null && record.size() == 3 && brokerNames.size() < 3)) {
                    FollowVarietyVO brokerData = new FollowVarietyVO();
                    brokerData.setStdSymbol(stdSymbol);
                    brokerData.setStdContract(stdContract);
                    brokerData.setBrokerName(null);
                    brokerData.setBrokerSymbol(null);
                    brokerData.setTemplateId(template); // 设置 template 字段
                    brokerData.setTemplateName(templateName);
                    FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
                    baseMapper.insert(entity);
                }
            }
        } catch (Exception e) {
            throw new ServerException("无法读取文件");
        }
    }


    public void addExcel(MultipartFile file, Integer template, String templateName) throws IOException {
        try {
            InputStream inputStream = file.getInputStream();
            Workbook workbook = new XSSFWorkbook(inputStream);
            Sheet sheet = workbook.getSheetAt(0);
            // 获取表头
            Row headerRow = sheet.getRow(0);
            List<String> brokerNames = new ArrayList<>();
            for (Cell cell : headerRow) {
                brokerNames.add(cell.getStringCellValue());
            }
            // 从第二行开始遍历数据
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                String stdContractStr = getCellStringValue(row.getCell(0));
                String stdSymbol = getCellStringValue(row.getCell(1));
                Integer stdContract = null;
                if (stdSymbol == null || stdSymbol.isEmpty()) continue;

                // 解析 stdContract 字段
                if (stdContractStr != null && !stdContractStr.isEmpty()) {
                    try {
                        stdContract = Integer.parseInt(stdContractStr.trim());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid stdContract value: " + stdContractStr);
                    }
                }
                // 更新所有具有相同 stdSymbol 的记录的 stdContract 值
                baseMapper.updateStdContractByStdSymbol(stdSymbol, stdContract);

                // 遍历 brokerName 列，处理 brokerSymbol 和 brokerName
                for (int i = 2; i < brokerNames.size(); i++) {
                    String brokerName = brokerNames.get(i);
                    String brokerSymbol = getCellStringValue(row.getCell(i));

                    FollowVarietyVO brokerData = new FollowVarietyVO();
                    brokerData.setStdContract(stdContract);
                    brokerData.setStdSymbol(stdSymbol);
                    brokerData.setBrokerName(brokerName.trim());
                    brokerData.setTemplateId(template); // 设置 template 字段
                    brokerData.setTemplateName(templateName);

                    if (brokerSymbol == null || brokerSymbol.isEmpty()) {
                        // brokerSymbol 为空的情况
                        brokerData.setBrokerSymbol(null);
                        FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
                        baseMapper.insert(entity);
                    } else {
                        // brokerSymbol 不为空的情况，可能包含多个符号
                        String[] brokerSymbolParts = brokerSymbol.split("/");
                        for (String symbol : brokerSymbolParts) {
                            brokerData.setBrokerSymbol(symbol.trim());
                            FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
                            try {
                                baseMapper.insert(entity);
                            } catch (Exception e) {
                                log.info("插入失败: " + brokerData.getBrokerName() + "-" + brokerData.getBrokerSymbol());
                            }
                        }
                    }
                }
                // 如果只有 stdSymbol 和 stdContract，没有 brokerName 和 brokerSymbol，也要保存
                if (row.getLastCellNum() == 2 || (stdContract != null && row.getLastCellNum() == 3 && brokerNames.size() < 3)) {
                    FollowVarietyVO brokerData = new FollowVarietyVO();
                    brokerData.setStdSymbol(stdSymbol);
                    brokerData.setStdContract(stdContract);
                    brokerData.setBrokerName(null);
                    brokerData.setBrokerSymbol(null);
                    brokerData.setTemplateId(template); // 设置 template 字段
                    brokerData.setTemplateName(templateName);

                    FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
                    // 插入或更新记录，确保 stdContract 和 stdSymbol 成功保存
                    LambdaQueryWrapper<FollowVarietyEntity> queryWrapper = Wrappers.lambdaQuery();
                    queryWrapper.eq(FollowVarietyEntity::getStdSymbol, stdSymbol);

                    // 如果存在则更新，否则插入
                    if (baseMapper.selectCount(queryWrapper) > 0) {
                        baseMapper.update(entity, queryWrapper);
                    } else {
                        baseMapper.insert(entity);
                    }
                }
            }
        } catch (Exception e) {
            throw new ServerException("无法读取文件");
        }
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((int) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return Boolean.toString(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }

    @Override
    public void export() {
//    List<FollowVarietyExcelVO> excelList = FollowVarietyConvert.INSTANCE.convertExcelList(list());
//        transService.transBatch(excelList);
//        ExcelUtils.excelExport(FollowVarietyExcelVO.class, "品种匹配", null, excelList);

    }

    @Override
    public void download(HttpServletResponse response) throws Exception {
        // 设置文件名和MIME类型
        String filename = "template.csv";
        response.setContentType("text/csv");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        // 加载CSV模板文件
        InputStream inputStream = resourceLoader.getResource("classpath:template/品种对应表 - （外汇对_贵金属_能源）.csv").getInputStream();

        // 将文件内容写入HTTP响应
        StreamUtils.copy(inputStream, response.getOutputStream());
    }

    @Override
    public PageResult<FollowVarietyVO> pageSmybol(FollowVarietyQuery query) {
        LambdaQueryWrapper<FollowVarietyEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.select(FollowVarietyEntity::getStdSymbol, FollowVarietyEntity::getStdContract)
                .groupBy(FollowVarietyEntity::getStdSymbol, FollowVarietyEntity::getStdContract)
                .eq(FollowVarietyEntity::getTemplateId, query.getTemplate());
        IPage<FollowVarietyEntity> page = baseMapper.selectPage(getPage(query), wrapper);
        return new PageResult<>(FollowVarietyConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }

    @Override
    public PageResult<FollowVarietyVO> pageSmybolList(FollowVarietyQuery query) {
        Page<FollowVarietyEntity> page = new Page<>(query.getPage(), query.getLimit());
        IPage<FollowVarietyEntity> resultPage = baseMapper.pageSymbolList(page, query.getStdSymbol());
        return new PageResult<>(FollowVarietyConvert.INSTANCE.convertList(resultPage.getRecords()), resultPage.getTotal());
    }

    public void exportCsv(ByteArrayOutputStream outputStream, Integer template) throws IOException {
        // 查询数据库所有数据，添加 template 过滤
        List<FollowPlatformVO> brokers = followPlatformServiceImpl.listBroke();
        // 获取券商名称列表
        List<String> brokerNames = brokers.stream()
                .map(FollowPlatformVO::getBrokerName)
                .toList();
        // 查询数据库所有数据，添加 template 过滤
        List<FollowVarietyExcelVO> data1 = FollowVarietyConvert.INSTANCE.convertExcelList(list())
                .stream()
                .filter(record -> record.getTemplateId() != null && record.getTemplateId().equals(template))
                .collect(Collectors.toList());

        List<FollowVarietyExcelVO> data = data1.stream()
                .filter(record -> brokerNames.contains(record.getBrokerName()))
                .collect(Collectors.toList());

        Set<String> stdSymbols = new LinkedHashSet<>();
        Set<String> brokerNamesSet = new LinkedHashSet<>();
        Map<String, Map<String, List<String>>> symbolBrokerMap = new HashMap<>();

        for (FollowVarietyExcelVO record : data) {
            String stdSymbol = record.getStdSymbol();
            String brokerName = record.getBrokerName();
            String brokerSymbol = record.getBrokerSymbol();

            stdSymbols.add(stdSymbol);
            brokerNamesSet.add(brokerName);

            // 使用 List 来存储 BrokerSymbols
            symbolBrokerMap
                    .computeIfAbsent(stdSymbol, k -> new HashMap<>())
                    .computeIfAbsent(brokerName, k -> new ArrayList<>())
                    .add(brokerSymbol);
        }

        try (CSVPrinter csvPrinter = new CSVPrinter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
            // 添加表头
            List<String> header = new ArrayList<>();
            header.add("stdContract"); // 第一列为 stdContract
            header.add("stdSymbol");    // 第二列为 stdSymbol
            header.addAll(brokerNamesSet); // 添加券商名称
            csvPrinter.printRecord(header);

            for (String stdSymbol : stdSymbols) {
                List<String> row = new ArrayList<>();
                // 添加 stdContract 和 stdSymbol
                String stdContract = data.stream()
                        .filter(record -> record.getStdSymbol().equals(stdSymbol))
                        .map(record -> String.valueOf(ObjectUtil.isEmpty(record.getStdContract()) ? "" : record.getStdContract())) // 将 Integer 转换为 String
                        .findFirst()
                        .orElse(""); // 获取对应的 stdContract，如果不存在则为空字符串

                row.add(stdContract); // 添加 stdContract
                row.add(stdSymbol);    // 添加 stdSymbol

                for (String brokerName : brokerNamesSet) {
                    Map<String, List<String>> brokerMap = symbolBrokerMap.get(stdSymbol);
                    String brokerSymbol = brokerMap != null && brokerMap.containsKey(brokerName)
                            ? String.join("/", brokerMap.get(brokerName)) // 用 / 连接多个 BrokerSymbols
                            : "";
                    row.add(brokerSymbol);
                }
                // 若有值等于null,全部转为""
                row.replaceAll(s -> "null".equals(s) ? "" : s);
                csvPrinter.printRecord(row);
            }

            for (FollowVarietyExcelVO record : data1) {
                String stdSymbol = record.getStdSymbol();
                String stdContract = ObjectUtil.isEmpty(record.getStdContract()) ? "" : String.valueOf(record.getStdContract());

                if (!stdSymbols.contains(stdSymbol) && !stdContract.isEmpty()) {
                    List<String> row = new ArrayList<>();
                    row.add(stdContract);
                    row.add(stdSymbol);
                    for (int i = 0; i < brokerNamesSet.size(); i++) {
                        row.add("");
                    }
                    csvPrinter.printRecord(row);
                    stdSymbols.add(stdSymbol);
                }
                if (!stdSymbols.contains(stdSymbol) && stdContract.isEmpty()) {
                    List<String> row = new ArrayList<>();
                    row.add("");
                    row.add(stdSymbol);

                    for (int i = 0; i < brokerNamesSet.size(); i++) {
                        row.add("");
                    }
                    csvPrinter.printRecord(row);
                    stdSymbols.add(stdSymbol);
                }
            }
        } catch (IOException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void generateCsv(ByteArrayOutputStream outputStream) throws IOException {
        List<FollowPlatformVO> brokers = followPlatformServiceImpl.listBroke();
        // 获取券商名称
        List<String> brokerNames = brokers.stream()
                .map(FollowPlatformVO::getBrokerName)
                .toList();

        // 使用相对路径替代绝对路径
        String inputFilePath = "/template/品种匹配导出模板.csv"; // 相对于 resources 目录的路径

        // 读取 CSV 文件
        try (InputStream inputStream = getClass().getResourceAsStream(inputFilePath);
             Reader in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(in, CSVFormat.DEFAULT.withFirstRecordAsHeader());
             CSVPrinter csvPrinter = new CSVPrinter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {

            if (inputStream == null) {
                throw new FileNotFoundException("未找到指定的模板文件：" + inputFilePath);
            }
            List<CSVRecord> records = parser.getRecords();

            // 写入表头
            List<String> header = new ArrayList<>();
            header.add("stdContract");
            header.add("stdSymbol");
            header.addAll(brokerNames);
            csvPrinter.printRecord(header);

            // 写入原始数据
            for (CSVRecord record : records) {
                List<String> row = new ArrayList<>();
                row.add(record.get("stdContract")); // stdContract
                row.add(record.get("stdSymbol")); // stdSymbol
                // 添加空白列以便后续券商数据填充
                for (int i = 0; i < brokerNames.size(); i++) {
                    row.add(""); // 填充空白列
                }
                csvPrinter.printRecord(row);
            }
        } catch (IOException e) {
            throw new IOException("写入 CSV 时出错", e);
        }
    }
}