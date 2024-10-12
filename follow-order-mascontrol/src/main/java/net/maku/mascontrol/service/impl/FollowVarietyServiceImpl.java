package net.maku.mascontrol.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.mascontrol.convert.FollowVarietyConvert;
import net.maku.mascontrol.entity.FollowVarietyEntity;
import net.maku.mascontrol.query.FollowPlatformQuery;
import net.maku.mascontrol.query.FollowVarietyQuery;
import net.maku.mascontrol.vo.FollowVarietyVO;
import net.maku.mascontrol.dao.FollowVarietyDao;
import net.maku.mascontrol.service.FollowVarietyService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.mascontrol.vo.FollowVarietyExcelVO;
import net.maku.framework.common.excel.ExcelFinishCallBack;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Override
    public PageResult<FollowVarietyVO> page(FollowVarietyQuery query) {
        IPage<FollowVarietyEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowVarietyConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowVarietyEntity> getWrapper(FollowVarietyQuery query){
        LambdaQueryWrapper<FollowVarietyEntity> wrapper = Wrappers.lambdaQuery();

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
////
////        return brokerDataList;
//
//        List<FollowVarietyExcelVO> brokerDataList = new ArrayList<>();
//        // 读取CSV文件
//        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
//             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
//            List<String> brokerNames = new ArrayList<>(csvParser.getHeaderMap().keySet());
//            // 从第二行开始，处理每个stdSymbol及其对应的brokerSymbol
//            for (CSVRecord record : csvParser) {
//                String stdSymbol = record.get(0);
//                // 遍历每个brokerName列
//                for (int i = 1; i < record.size(); i++) {
//                    String[] brokerNameParts = brokerNames.get(i).split("/"); // 处理 brokerName 中的 '/'
//                    String brokerSymbol = record.get(i);
//                    // 处理 brokerSymbol 中的 '/'
//                    String[] brokerSymbolParts = brokerSymbol.split("/");
//                    // 生成所有组合
//                    for (String name : brokerNameParts) {
//                        for (String symbol : brokerSymbolParts) {
//                            FollowVarietyExcelVO brokerData = new FollowVarietyExcelVO();
//                            brokerData.setStdSymbol(stdSymbol);
//                            brokerData.setBrokerName(name.trim());
//                            brokerData.setBrokerSymbol(symbol.trim());
//                            brokerDataList.add(brokerData);
//                        }
//                    }
//                }
//            }
//        }
////        return brokerDataList;
//
//    }
    public  List<FollowVarietyExcelVO> importByExcel(MultipartFile file) throws Exception {
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
                if (ObjectUtil.isEmpty(record.get(0)))continue;
                String stdSymbol = record.get(0);
                for (int i = 1; i < record.size(); i++) {
                    if (ObjectUtil.isEmpty(record.get(i)))continue;

                    String[] brokerNameParts = brokerNames.get(i).split("/");
                    String brokerSymbol = record.get(i);
                    String[] brokerSymbolParts = brokerSymbol.split("/");

                    for (String name : brokerNameParts) {
                        for (String symbol : brokerSymbolParts) {
                            FollowVarietyVO brokerData = new FollowVarietyVO();
                            brokerData.setStdSymbol(stdSymbol);
                            brokerData.setBrokerName(name.trim());
                            brokerData.setBrokerSymbol(symbol.trim());
                            try {
                                baseMapper.insert(FollowVarietyConvert.INSTANCE.convert(brokerData));
                            }catch (Exception e){
                                log.error("Error inserting data: ", e);
                                log.error(e.getMessage());
                            }
                        }
                    }
                }
            }
        }
    }

    private void importExcel(MultipartFile file, List<FollowVarietyExcelVO> brokerDataList) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            List<String> brokerNames = new ArrayList<>();

            Row headerRow = sheet.getRow(0);

            for (Cell cell : headerRow) {
                brokerNames.add(cell.getStringCellValue());
            }

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                if (ObjectUtil.isEmpty(row.getCell(0)))continue;
                String stdSymbol = row.getCell(0).getStringCellValue();

                for (int i = 1; i < row.getPhysicalNumberOfCells(); i++) {
                    Cell currentCell = row.getCell(i);
                    if (currentCell == null || currentCell.getCellType() != CellType.STRING) continue;

                    String[] brokerNameParts = brokerNames.get(i).split("/");
                    String brokerSymbol = row.getCell(i).getStringCellValue();
                    String[] brokerSymbolParts = brokerSymbol.split("/");

                    for (String name : brokerNameParts) {
                        for (String symbol : brokerSymbolParts) {
                            FollowVarietyVO brokerData = new FollowVarietyVO();
                            brokerData.setStdSymbol(stdSymbol);
                            brokerData.setBrokerName(name.trim());
                            brokerData.setBrokerSymbol(symbol.trim());
                            try {
                                baseMapper.insert(FollowVarietyConvert.INSTANCE.convert(brokerData));
                            }catch (Exception e){
                                log.error("Error inserting data: ", e);
                                log.error(e.getMessage());
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void export() {
    List<FollowVarietyExcelVO> excelList = FollowVarietyConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowVarietyExcelVO.class, "品种匹配", null, excelList);
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
        wrapper.select(FollowVarietyEntity::getStdSymbol).groupBy(FollowVarietyEntity::getStdSymbol);
        IPage<FollowVarietyEntity> page = baseMapper.selectPage(getPage(query),wrapper);
        return new PageResult<>(FollowVarietyConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }

    @Override
    public PageResult<FollowVarietyVO> pageSmybolList(FollowVarietyQuery query) {
        LambdaQueryWrapper<FollowVarietyEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(FollowVarietyEntity::getStdSymbol,query.getStdSymbol());
        IPage<FollowVarietyEntity> page = baseMapper.selectPage(getPage(query),wrapper);
        return new PageResult<>(FollowVarietyConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
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