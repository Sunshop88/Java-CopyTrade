package net.maku.followcom.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.followcom.convert.FollowVarietyConvert;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowVarietyEntity;
import net.maku.followcom.query.FollowPlatformQuery;
import net.maku.followcom.query.FollowVarietyQuery;
import net.maku.followcom.vo.FollowPlatformVO;
import net.maku.followcom.vo.FollowVarietyVO;
import net.maku.followcom.dao.FollowVarietyDao;
import net.maku.followcom.service.FollowVarietyService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.followcom.vo.FollowVarietyExcelVO;
import net.maku.framework.common.excel.ExcelFinishCallBack;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
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
    @Autowired
    private ResourceLoader resourceLoader;
    @Autowired
    private FollowVarietyDao followVarietyDao;
    @Autowired
    private FollowPlatformServiceImpl followPlatformServiceImpl;

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

    @Override
//    public  void importByExcel(MultipartFile file) throws Exception{
////        List<FollowVarietyExcelVO> brokerDataList = new ArrayList<>();
////
////        try (CSVReader csvReader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
////            String[] headers = csvReader.readNext(); // 读取表头
////            String[] row;
////
////            while ((row = csvReader.readNext()) != null) {
////                String stdSymbol = row[0];
////
////                for (int i = 1; i < headers.length; i++) {
////                    String brokerName = headers[i];
////                    String brokerSymbol = row[i];
////
////                    // 处理 brokerSymbol 中的 /
////                    if (brokerSymbol.contains("/")) {
////                        String[] symbols = brokerSymbol.split("/");
////                        for (String symbol : symbols) {
////                            brokerDataList.add(new FollowVarietyExcelVO(stdSymbol, brokerName, symbol.trim()));
////                        }
////                    } else {
////                        brokerDataList.add(new FollowVarietyExcelVO(stdSymbol, brokerName, brokerSymbol.trim()));
////                    }
////                }
////
////                // 处理 brokerName 中的 /
////                if (brokerName.contains("/")) {
////                    String[] names = brokerName.split("/");
////                    for (String name : names) {
////                        String brokerSymbol = row[i]; // 取最后一个 brokerSymbol
////                        brokerDataList.add(new FollowVarietyExcelVO(stdSymbol, name.trim(), brokerSymbol.trim()));
////                    }
////                }
////            }
////        }

////        return brokerDataList;
    public List<FollowVarietyExcelVO> importByExcel(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        List<FollowVarietyExcelVO> brokerDataList = new ArrayList<>();

        if (fileName != null && fileName.toLowerCase().endsWith(".csv")) {
            // 处理CSV文件
            importCsv(file, brokerDataList);
        } else if (fileName != null && (fileName.toLowerCase().endsWith(".xls") || fileName.toLowerCase().endsWith(".xlsx"))) {
            // 处理Excel文件
            importExcel(file, brokerDataList);
        } else {
            throw new Exception("Unsupported file type. Please upload a CSV or Excel file.");
        }
        // 将处理后的数据brokerDataList保存数据到数据库
//        List <FollowVarietyEntity> brokerDataList2 = FollowVarietyConvert.INSTANCE.convertExcelList2(brokerDataList);
        return brokerDataList;
    }


//    public void importCsv(MultipartFile file, List<FollowVarietyExcelVO> brokerDataList) throws IOException {
//        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
//             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
//
//            List<String> brokerNames = new ArrayList<>(csvParser.getHeaderMap().keySet());
//
//            for (CSVRecord record : csvParser) {
//                if (ObjectUtil.isEmpty(record.get(1))) continue;
//                String stdContractStr = record.get(0);
//                String stdSymbol = record.get(1);
//                Integer stdContract = null;
//
//                // 检查 stdContractStr 是否为空字符串并进行转换
//                if (!ObjectUtil.isEmpty(stdContractStr)) {
//                    try {
//                        stdContract = Integer.valueOf(stdContractStr.trim());
//                    } catch (NumberFormatException e) {
//                        log.warn("Invalid stdContract value: " + stdContractStr);
//                    }
//                }
//
//                // 更新所有具有相同 stdSymbol 的记录的 stdContract 值
//                baseMapper.updateStdContractByStdSymbol(stdSymbol, stdContract);
//
//                System.out.println(record.size());
//                // 如果只有 stdSymbol 和 stdContract，则直接插入记录，不处理 brokerSymbol 和 brokerName
//                if (brokerNames.size() <= 2 ) {
//                    FollowVarietyVO brokerData = new FollowVarietyVO();
//                    brokerData.setStdContract(stdContract);
//                    brokerData.setStdSymbol(stdSymbol);
//                    brokerData.setBrokerName(null);
//                    brokerData.setBrokerSymbol(null);
//
//                    FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
//
//                    try {
//                        // 检查是否已经存在具有相同 stdSymbol 的记录
//                        LambdaQueryWrapper<FollowVarietyEntity> queryWrapper = Wrappers.lambdaQuery();
//                        queryWrapper.eq(FollowVarietyEntity::getStdSymbol, stdSymbol);
//
//                        // 如果存在记录则更新，否则插入
//                        if (baseMapper.selectCount(queryWrapper) > 0) {
//                            baseMapper.update(entity, queryWrapper);
//                        } else {
//                            baseMapper.insert(entity);
//                        }
//                    } catch (Exception e) {
//                        log.info("插入或更新失败: " + stdSymbol);
//                    }
//                    continue;
//                }
//
//                // 处理每个 brokerName
//                for (int i = 2; i < record.size(); i++) {
//                    String brokerName = brokerNames.get(i);
//                    String brokerSymbol = record.get(i);
//                    String[] brokerNameParts = brokerName.split("/");
//
//                    for (String name : brokerNameParts) {
//                        // 删除已有的相同 stdSymbol 和 brokerName 对应的 brokerSymbol
//                        LambdaQueryWrapper<FollowVarietyEntity> deleteQueryWrapper = Wrappers.lambdaQuery();
//                        deleteQueryWrapper.eq(FollowVarietyEntity::getStdSymbol, stdSymbol)
//                                .eq(FollowVarietyEntity::getBrokerName, name.trim());
//                        baseMapper.delete(deleteQueryWrapper);
//
//                        // 如果 brokerSymbol 不为空，则插入新的记录
//                        if (!ObjectUtil.isEmpty(brokerSymbol)) {
//                            String[] brokerSymbolParts = brokerSymbol.split("/");
//
//                            for (String symbol : brokerSymbolParts) {
//                                FollowVarietyVO brokerData = new FollowVarietyVO();
//                                brokerData.setStdContract(stdContract);
//                                brokerData.setStdSymbol(stdSymbol);
//                                brokerData.setBrokerName(name.trim());
//                                brokerData.setBrokerSymbol(symbol.trim());
//
//                                // 确保 stdContract 字段总是被更新
//                                FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
//                                try {
//                                    baseMapper.insert(entity);
//                                } catch (Exception e) {
//                                    log.info("插入失败: " + brokerData.getBrokerName() + "-" + brokerData.getBrokerSymbol());
//                                }
//                            }
//                        }
//                    }
//                }
//                // 处理列数不匹配的情况
//                if (record.size() != brokerNames.size()) {
//                    for (int i = brokerNames.size() - 1; i < brokerNames.size(); i--) {
//                        String[] brokerNameParts = brokerNames.get(i).split("/");
//                        for (String name : brokerNameParts) {
//                            // 进行补充删除处理
//                            LambdaQueryWrapper<FollowVarietyEntity> deleteQueryWrapper = Wrappers.lambdaQuery();
//                            deleteQueryWrapper.eq(FollowVarietyEntity::getStdSymbol, stdSymbol)
//                                    .eq(FollowVarietyEntity::getBrokerName, name.trim());
//                            baseMapper.delete(deleteQueryWrapper);
//                        }
//                        if (i == record.size()) break;
//                    }
//                }
//            }
//        }
//    }


    public void importCsv(MultipartFile file, List<FollowVarietyExcelVO> brokerDataList) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
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
                for (int i = 2; i < record.size(); i++) {
                    String brokerName = brokerNames.get(i);
                    String brokerSymbol = record.get(i);

                    // 删除已有的相同 stdSymbol 和 brokerName 对应的 brokerSymbol，即便 brokerSymbol 为 null
                    LambdaQueryWrapper<FollowVarietyEntity> deleteQueryWrapper = Wrappers.lambdaQuery();
                    deleteQueryWrapper.eq(FollowVarietyEntity::getStdSymbol, stdSymbol)
                            .eq(FollowVarietyEntity::getBrokerName, brokerName.trim());
                    baseMapper.delete(deleteQueryWrapper);

                    FollowVarietyVO brokerData = new FollowVarietyVO();
                    brokerData.setStdContract(stdContract);
                    brokerData.setStdSymbol(stdSymbol);
                    brokerData.setBrokerName(brokerName.trim());

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

                    FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
                    baseMapper.insert(entity);
                }
            }
        }
    }


    public void importExcel(MultipartFile file, List<FollowVarietyExcelVO> brokerDataList) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
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

                    // 删除已有的相同 stdSymbol 和 brokerName 对应的 brokerSymbol，即便 brokerSymbol 为 null
                    LambdaQueryWrapper<FollowVarietyEntity> deleteQueryWrapper = Wrappers.lambdaQuery();
                    deleteQueryWrapper.eq(FollowVarietyEntity::getStdSymbol, stdSymbol)
                            .eq(FollowVarietyEntity::getBrokerName, brokerName.trim());
                    baseMapper.delete(deleteQueryWrapper);

                    FollowVarietyVO brokerData = new FollowVarietyVO();
                    brokerData.setStdContract(stdContract);
                    brokerData.setStdSymbol(stdSymbol);
                    brokerData.setBrokerName(brokerName.trim());

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


//    public void importExcel(MultipartFile file, List<FollowVarietyExcelVO> brokerDataList) throws IOException {
//        try (InputStream inputStream = file.getInputStream()) {
//            Workbook workbook = new XSSFWorkbook(inputStream);
//            Sheet sheet = workbook.getSheetAt(0); // 获取第一个工作表
//
//            List<String> brokerNames = new ArrayList<>();
//
//            // 读取表头
//            Row headerRow = sheet.getRow(0);
//            for (Cell cell : headerRow) {
//                brokerNames.add(getCellValueAsString(cell));
//            }
//
//            // 从第二行开始读取数据
//            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
//                Row row = sheet.getRow(rowIndex);
//
//                String stdContractStr = getCellValueAsString(row.getCell(0));
//                String stdSymbol = getCellValueAsString(row.getCell(1));
//                Integer stdContract = null;
//
//                // 检查 stdContractStr 是否为空字符串并进行转换
//                if (!ObjectUtil.isEmpty(stdContractStr)) {
//                    try {
//                        double doubleValue = Double.parseDouble(stdContractStr.trim ());
//                        stdContract = (int) doubleValue;
//                    } catch (NumberFormatException e) {
//                        log.warn("Invalid stdContract value: " + stdContractStr);
//                    }
//                }
//                // 更新所有具有相同 stdSymbol 的记录的 stdContract 值，包括覆盖那些 stdContract 为空的值
//                baseMapper.updateStdContractByStdSymbol(stdSymbol, stdContract);
//
//
//                for (int i = 2; i < row.getLastCellNum(); i++) {
//                    Cell cell = row.getCell(i);
////                    if (cell == null || cell.getCellType() == CellType.BLANK) continue;
//
//                    String[] brokerNameParts = brokerNames.get(i).split("/");
//                    String brokerSymbol = getCellValueAsString(cell);
////                    String[] brokerSymbolParts = brokerSymbol.split("/");
//
//                    for (String name : brokerNameParts) {
//                        // 删除已有的相同 stdSymbol 和 brokerName 对应的 brokerSymbol
//                        LambdaQueryWrapper<FollowVarietyEntity> deleteQueryWrapper = Wrappers.lambdaQuery();
//                        deleteQueryWrapper.eq(FollowVarietyEntity::getStdSymbol, stdSymbol)
//                                .eq(FollowVarietyEntity::getBrokerName, name.trim());
//                        baseMapper.delete(deleteQueryWrapper);
//
//                        // 如果 brokerSymbol 不为空，则插入新的记录
//                        if (!ObjectUtil.isEmpty(brokerSymbol)) {
//                            String[] brokerSymbolParts = brokerSymbol.split("/");
//
//                            for (String symbol : brokerSymbolParts) {
//                                FollowVarietyVO brokerData = new FollowVarietyVO();
//                                brokerData.setStdContract(stdContract);
//                                brokerData.setStdSymbol(stdSymbol);
//                                brokerData.setBrokerName(name.trim());
//                                brokerData.setBrokerSymbol(symbol.trim());
//
//                                // 确保 stdContract 字段总是被更新
//                                FollowVarietyEntity entity = FollowVarietyConvert.INSTANCE.convert(brokerData);
//                                try {
//                                    baseMapper.insert(entity);
//                                } catch (Exception e) {
//                                    log.info("插入失败: " + brokerData.getBrokerName() + "-" + brokerData.getBrokerSymbol());
//                                }
//                            }
//                        }
//                    }
//                }
//
//                if (row.getLastCellNum()!=brokerNames.size()){
//                    for(int i=brokerNames.size()-1;i<brokerNames.size();i--){
//                        String[] brokerNameParts = brokerNames.get(i).split("/");
//                        for (String name : brokerNameParts) {
//                            //进行补充删除处理
//                            LambdaQueryWrapper<FollowVarietyEntity> deleteQueryWrapper = Wrappers.lambdaQuery();
//                            deleteQueryWrapper.eq(FollowVarietyEntity::getStdSymbol, stdSymbol)
//                                    .eq(FollowVarietyEntity::getBrokerName, name.trim());
//                            baseMapper.delete(deleteQueryWrapper);
//                        }
//                        if (i==row.getLastCellNum())break;
//                    }
//                }
//            }
//        }
//    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
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
    public List<FollowVarietyVO> getlist(String stdSymbol) {
        //根据品种名称来查询券商名称和券商对应的品种名称
        return FollowVarietyConvert.INSTANCE.convertList(baseMapper.getlist(stdSymbol));
    }

    @Override
    public PageResult<FollowVarietyVO> pageSmybol(FollowVarietyQuery query) {
        LambdaQueryWrapper<FollowVarietyEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.select(FollowVarietyEntity::getStdSymbol, FollowVarietyEntity::getStdContract)
                .groupBy(FollowVarietyEntity::getStdSymbol, FollowVarietyEntity::getStdContract)
                .like(StrUtil.isNotBlank(query.getStdSymbol()), FollowVarietyEntity::getStdSymbol, query.getStdSymbol());
        IPage<FollowVarietyEntity> page = baseMapper.selectPage(getPage(query), wrapper);
        return new PageResult<>(FollowVarietyConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }

    @Override
    public PageResult<FollowVarietyVO> pageSmybolList(FollowVarietyQuery query) {
        Page<FollowVarietyEntity> page = new Page<>(query.getPage(), query.getLimit());
        IPage<FollowVarietyEntity> resultPage = baseMapper.pageSymbolList(page, query.getStdSymbol());
        return new PageResult<>(FollowVarietyConvert.INSTANCE.convertList(resultPage.getRecords()), resultPage.getTotal());
    }


    @Override
    public void exportCsv(ByteArrayOutputStream outputStream) throws IOException {
        // 查询数据库所有数据
        List<FollowPlatformVO> brokers = followPlatformServiceImpl.listBroke();

        // 获取券商名称列表
        List<String> brokerNames = brokers.stream()
                .map(FollowPlatformVO::getBrokerName)
                .toList();

        // 查询数据库所有数据
        List<FollowVarietyExcelVO> data1 = FollowVarietyConvert.INSTANCE.convertExcelList(list());

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
//    @Override
//    public void generateCsv(ByteArrayOutputStream outputStream) throws IOException {
//        List<FollowPlatformVO> brokers = followPlatformServiceImpl.listBroke();
//
//        // 获取券商名称
//        List<String> brokerNames = brokers.stream()
//                .map(FollowPlatformVO::getBrokerName)
//                .toList();
//
//        // 使用相对路径替代绝对路径
//        String inputFilePath = "/template/品种匹配导出模板.csv"; // 相对于 resources 目录的路径
//
//        // 读取 CSV 文件
//        try (InputStream inputStream = getClass().getResourceAsStream(inputFilePath);
//             Reader in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
//             CSVParser parser = new CSVParser(in, CSVFormat.DEFAULT.withFirstRecordAsHeader());
//             CSVPrinter csvPrinter = new CSVPrinter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
//
//            if (inputStream == null) {
//                throw new FileNotFoundException("未找到指定的模板文件：" + inputFilePath);
//            }
//
//            List<CSVRecord> records = parser.getRecords();
//
//            // 写入表头
//            List<String> header = new ArrayList<>();
//            header.add("stdContract");
//            header.addAll(brokerNames);
//            csvPrinter.printRecord(header);
//
//            // 写入原始数据
//            for (CSVRecord record : records) {
//                List<String> row = new ArrayList<>();
//                row.add(record.get(0)); // stdSymbol
//                // 添加空白列以便后续券商数据填充
//                for (int i = 0; i < brokerNames.size(); i++) {
//                    row.add(""); // 填充空白列
//                }
//                csvPrinter.printRecord(row);
//            }
//        } catch (IOException e) {
//            throw new IOException("写入 CSV 时出错", e);
//        }
//    }

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

    @Override
    public List<String> listSymbol() {
        // 查询所有品种,因为品种有重复的，要先判断，相同的品种取一次就可以
        Set<String> uniqueSymbols = new HashSet<>(); // 使用HashSet来存储唯一的品种
        List<String> allSymbols = baseMapper.queryVarieties(); // 假设此方法返回所有品种的列表

        for (String symbol : allSymbols) {
            uniqueSymbols.add(symbol); // 将查询到的所有品种添加到Set中，自动去重
        }

        return new ArrayList<>(uniqueSymbols); // 将Set转换为List并返回
    }


}