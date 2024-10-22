package net.maku.followcom.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
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

    private void importCsv(MultipartFile file, List<FollowVarietyExcelVO> brokerDataList) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            List<String> brokerNames = new ArrayList<>(csvParser.getHeaderMap().keySet());

            for (CSVRecord record : csvParser) {
                if (ObjectUtil.isEmpty(record.get(0)) || ObjectUtil.isEmpty(record.get(1))) continue;
                String stdContract = record.get(0);
                String stdSymbol = record.get(1);

                for (int i = 2; i < record.size(); i++) {
                    if (ObjectUtil.isEmpty(record.get(i))) continue;

                    String[] brokerNameParts = brokerNames.get(i).split("/");
                    String brokerSymbol = record.get(i);
                    String[] brokerSymbolParts = brokerSymbol.split("/");

                    for (String name : brokerNameParts) {
                        for (String symbol : brokerSymbolParts) {
                            FollowVarietyVO brokerData = new FollowVarietyVO();
                            brokerData.setStdContract(Integer.valueOf(stdContract));
                            brokerData.setStdSymbol(stdSymbol);
                            brokerData.setBrokerName(name.trim());
                            brokerData.setBrokerSymbol(symbol.trim());

                            LambdaQueryWrapper<FollowVarietyEntity> queryWrapper = Wrappers.lambdaQuery();
                            queryWrapper.eq(FollowVarietyEntity::getStdSymbol, stdSymbol)
                                    .eq(FollowVarietyEntity::getBrokerName, name.trim())
                                    .eq(FollowVarietyEntity::getBrokerSymbol, symbol.trim());

                            int updateCount = baseMapper.update(FollowVarietyConvert.INSTANCE.convert(brokerData), queryWrapper);
                            if (updateCount == 0) {
                                try {
                                    baseMapper.insert(FollowVarietyConvert.INSTANCE.convert(brokerData));
                                } catch (Exception e) {
                                    log.info("插入失败: " + brokerData.getBrokerName() + "-" + brokerData.getBrokerSymbol());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

//    private void importExcel(MultipartFile file, List<FollowVarietyExcelVO> brokerDataList) throws IOException {
//        try (InputStream inputStream = file.getInputStream();
//             Workbook workbook = new XSSFWorkbook(inputStream)) {
//            Sheet sheet = workbook.getSheetAt(0); // 获取第一个工作表
//            List<String> brokerNames = new ArrayList<>();
//
//            Row headerRow = sheet.getRow(0);
//            for (Cell cell : headerRow) {
//                brokerNames.add(cell.getStringCellValue());
//            }
//
//            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
//                Row row = sheet.getRow(rowIndex);
//                if (row == null) continue;
//
//                // 检查 stdContract 和 stdSymbol 是否为空
//                if (ObjectUtil.isEmpty(row.getCell(0)) || ObjectUtil.isEmpty(row.getCell(1))) continue;
//
//                String stdContract = String.valueOf(row.getCell(0).getNumericCellValue());
//                String stdSymbol = row.getCell(1).getStringCellValue();
//
//                for (int i = 2; i < row.getPhysicalNumberOfCells(); i++) {
//                    Cell cell = row.getCell(i);
//                    if (cell == null || ObjectUtil.isEmpty(cell.getStringCellValue())) continue;
//
//                    String[] brokerNameParts = brokerNames.get(i).split("/");
//                    String brokerSymbol = cell.getStringCellValue();
//                    String[] brokerSymbolParts = brokerSymbol.split("/");
//
//                    for (String name : brokerNameParts) {
//                        for (String symbol : brokerSymbolParts) {
//                            int stdContractValue;
//                            try {
//                                stdContractValue = (int) Double.parseDouble(stdContract);
//                            } catch (NumberFormatException e) {
//                                log.error("无法解析 stdContract: " + stdContract, e);
//                                continue;
//                            }
//                            FollowVarietyVO brokerData = new FollowVarietyVO();
//                            brokerData.setStdContract(stdContractValue);
//                            brokerData.setStdSymbol(stdSymbol);
//                            brokerData.setBrokerName(name.trim());
//                            brokerData.setBrokerSymbol(symbol.trim());
//                            try {
//                                baseMapper.insert(FollowVarietyConvert.INSTANCE.convert(brokerData));
//                            } catch (Exception e) {
//                                log.info("插入失败: " + brokerData.getBrokerName() + "-" + brokerData.getBrokerSymbol());
//                            }
//                        }
//                    }
//                }
//            }
//        } catch (Exception e) {
//            log.error("导入 Excel 失败", e);
//            throw new IOException("导入 Excel 失败", e);
//        }
//    }

    private void importExcel(MultipartFile file, List<FollowVarietyExcelVO> brokerDataList) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            List<String> brokerNames = new ArrayList<>();

            for (Cell cell : headerRow) {
                brokerNames.add(cell.getStringCellValue());
            }

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // 跳过标题行

                Cell stdContractCell = row.getCell(0);
                Cell stdSymbolCell = row.getCell(1);

                if (ObjectUtils.isEmpty(stdContractCell) || ObjectUtils.isEmpty(stdSymbolCell)) continue;

                String stdContract1 = String.valueOf(row.getCell(0).getNumericCellValue());
                String stdSymbol = stdSymbolCell.getStringCellValue();
                double stdContract =  Double.parseDouble(stdContract1);

                for (int i = 2; i < row.getLastCellNum(); i++) {
                    Cell brokerSymbolCell = row.getCell(i);

                    if (ObjectUtils.isEmpty(brokerSymbolCell)) continue;

                    String[] brokerNameParts = brokerNames.get(i).split("/");
                    String brokerSymbol = brokerSymbolCell.getStringCellValue();
                    String[] brokerSymbolParts = brokerSymbol.split("/");

                    for (String name : brokerNameParts) {
                        for (String symbol : brokerSymbolParts) {
                            FollowVarietyVO brokerData = new FollowVarietyVO();
                            brokerData.setStdContract((int) stdContract);
                            brokerData.setStdSymbol(stdSymbol);
                            brokerData.setBrokerName(name.trim());
                            brokerData.setBrokerSymbol(symbol.trim());

                            LambdaQueryWrapper<FollowVarietyEntity> queryWrapper = Wrappers.lambdaQuery();
                            queryWrapper.eq(FollowVarietyEntity::getStdSymbol, stdSymbol)
                                    .eq(FollowVarietyEntity::getBrokerName, name.trim())
                                    .eq(FollowVarietyEntity::getBrokerSymbol, symbol.trim());

                            int updateCount = baseMapper.update(FollowVarietyConvert.INSTANCE.convert(brokerData), queryWrapper);
                            if (updateCount == 0) {
                                try {
                                    baseMapper.insert(FollowVarietyConvert.INSTANCE.convert(brokerData));
                                } catch (Exception e) {
                                    log.info("插入失败: " + brokerData.getBrokerName() + "-" + brokerData.getBrokerSymbol());
                                }
                            }
                        }
                    }
                }
            }
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
//    public void exportCsv(ByteArrayOutputStream outputStream) throws IOException {
////        // 查询数据库所有数据
////        List<FollowVarietyExcelVO> data = FollowVarietyConvert.INSTANCE.convertExcelList(list());
//        List<FollowPlatformVO> brokers = followPlatformServiceImpl.listBroke();
//
//// 获取券商名称列表
//        List<String> brokerNamess = brokers.stream()
//                .map(FollowPlatformVO::getBrokerName)
//                .toList();
//
//        //查询数据库所有数据
//        List<FollowVarietyExcelVO> data1 = FollowVarietyConvert.INSTANCE.convertExcelList(list());
//
//        List<FollowVarietyExcelVO> data = data1.stream()
//                .filter(record -> brokerNamess.contains(record.getBrokerName()))
//                .collect(Collectors.toList());
//
//        Set<String> stdSymbols = new LinkedHashSet<>();
//        Set<String> brokerNames = new LinkedHashSet<>();
//        Map<String, Map<String, List<String>>> symbolBrokerMap = new HashMap<>();
//
//        for (FollowVarietyExcelVO record : data) {
//            String stdContract = String.valueOf(record.getStdContract());
//            String stdSymbol = record.getStdSymbol();
//            String brokerName = record.getBrokerName();
//            String brokerSymbol = record.getBrokerSymbol();
//
//            stdSymbols.add(stdSymbol);
//            brokerNames.add(brokerName);
//
//            // 使用 List 来存储 BrokerSymbols
//            symbolBrokerMap
//                    .computeIfAbsent(stdSymbol, k -> new HashMap<>())
//                    .computeIfAbsent(brokerName, k -> new ArrayList<>())
//                    .add(brokerSymbol);
//        }
//
//        try (CSVPrinter csvPrinter = new CSVPrinter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
//
//            List<String> header = new ArrayList<>();
//            header.add("symbol");
//            header.addAll(brokerNames);
//            csvPrinter.printRecord(header);
//
//            for (String stdSymbol : stdSymbols) {
//                List<String> row = new ArrayList<>();
//                row.add(stdSymbol);
//
//                for (String brokerName : brokerNames) {
//                    Map<String, List<String>> brokerMap = symbolBrokerMap.get(stdSymbol);
//                    String brokerSymbol = brokerMap != null && brokerMap.containsKey(brokerName)
//                            ? String.join("/", brokerMap.get(brokerName)) // 用 / 连接多个 BrokerSymbols
//                            : "";
//                    row.add(brokerSymbol);
//                }
//
//                csvPrinter.printRecord(row);
//            }
//
//        } catch (IOException e) {
//            throw new IOException(e);
//        }
//    }

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
                String stdContract = String.valueOf(data.stream()
                        .filter(record -> record.getStdSymbol().equals(stdSymbol))
                        .map(FollowVarietyExcelVO::getStdContract)
                        .findFirst()
                        .orElse(Integer.valueOf("0"))); // 获取对应的 stdContract

                row.add(stdContract); // 添加 stdContract
                row.add(stdSymbol);    // 添加 stdSymbol

                for (String brokerName : brokerNamesSet) {
                    Map<String, List<String>> brokerMap = symbolBrokerMap.get(stdSymbol);
                    String brokerSymbol = brokerMap != null && brokerMap.containsKey(brokerName)
                            ? String.join("/", brokerMap.get(brokerName)) // 用 / 连接多个 BrokerSymbols
                            : "";
                    row.add(brokerSymbol);
                }

                csvPrinter.printRecord(row);
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