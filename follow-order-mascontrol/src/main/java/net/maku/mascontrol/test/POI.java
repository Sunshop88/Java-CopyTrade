package net.maku.mascontrol.test;


import net.maku.mascontrol.convert.FollowVarietyConvert;
import net.maku.mascontrol.entity.FollowVarietyEntity;
import net.maku.mascontrol.service.impl.FollowVarietyServiceImpl;
import net.maku.mascontrol.vo.FollowVarietyExcelVO;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
public class POI {

//    public static void main(String[] args) {
//        // 创建模拟的 MultipartFile 对象
//        MultipartFile mockFile = createMockFile("品种对应表.csv", "stdSymbol,brokerName,brokerSymbol\n" +
//                "symbol1,broker1/broker2,symbol1a/symbol1b\n" +
//                "symbol2,broker3/broker4,symbol2a/symbol2b");
//
//        // 调用 importByFile 方法
//        try {
//            List<FollowVarietyExcelVO> brokerDataList = importByFile(mockFile);
//            // 打印处理后的数据
//            brokerDataList.forEach(System.out::println);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    public static List<FollowVarietyExcelVO> importByUrl(String url) throws Exception {
//        InputStream inputStream = getFileFromUrl(url);
//
//        String fileName = getFileNameFromUrl(url);
//        List<FollowVarietyExcelVO> brokerDataList = new ArrayList<>();
//
//        if (fileName != null && fileName.toLowerCase().endsWith(".csv")) {
//            // 处理CSV文件
//            importCsv(inputStream, brokerDataList);
//        } else if (fileName != null && (fileName.toLowerCase().endsWith(".xls") || fileName.toLowerCase().endsWith(".xlsx"))) {
//            // 处理Excel文件
//            importExcel(inputStream, brokerDataList);
//        } else {
//            throw new Exception("Unsupported file type. Please provide a CSV or Excel file.");
//        }
//
//        // 返回处理后的数据列表
//        return brokerDataList;
//    }
//
//    private static void importCsv(MultipartFile file, List<FollowVarietyExcelVO> brokerDataList) throws IOException {
//        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
//             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
//            List<String> brokerNames = new ArrayList<>(csvParser.getHeaderMap().keySet());
//
//            for (CSVRecord record : csvParser) {
//                String stdSymbol = record.get(0);
//                for (int i = 1; i < record.size(); i++) {
//                    String[] brokerNameParts = brokerNames.get(i).split("/");
//                    String brokerSymbol = record.get(i);
//                    String[] brokerSymbolParts = brokerSymbol.split("/");
//
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
//    }
//
//    private static void importExcel(MultipartFile file, List<FollowVarietyExcelVO> brokerDataList) throws IOException {
//        try (InputStream inputStream = file.getInputStream();
//             Workbook workbook = new XSSFWorkbook(inputStream)) {
//
//            Sheet sheet = workbook.getSheetAt(0);
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
//                String stdSymbol = row.getCell(0).getStringCellValue();
//
//                for (int i = 1; i < row.getPhysicalNumberOfCells(); i++) {
//                    String[] brokerNameParts = brokerNames.get(i).split("/");
//                    String brokerSymbol = row.getCell(i).getStringCellValue();
//                    String[] brokerSymbolParts = brokerSymbol.split("/");
//
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
//    }


    public static List<FollowVarietyExcelVO> importByFile(String filePath) throws Exception {
        File file = new File(filePath);
        String fileName = file.getName();
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
        List <FollowVarietyEntity> brokerDataList2 = FollowVarietyConvert.INSTANCE.convertExcelList2(brokerDataList);
//        this.saveBatch(brokerDataList2);
        // 返回处理后的数据列表
        return brokerDataList;
    }

    private static void importCsv(File file, List<FollowVarietyExcelVO> brokerDataList) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            List<String> brokerNames = new ArrayList<>(csvParser.getHeaderMap().keySet());

            for (CSVRecord record : csvParser) {
                String stdSymbol = record.get(0);
                for (int i = 1; i < record.size(); i++) {
                    String[] brokerNameParts = brokerNames.get(i).split("/");
                    String brokerSymbol = record.get(i);
                    String[] brokerSymbolParts = brokerSymbol.split("/");

                    for (String name : brokerNameParts) {
                        for (String symbol : brokerSymbolParts) {
                            FollowVarietyExcelVO brokerData = new FollowVarietyExcelVO();
                            brokerData.setStdSymbol(stdSymbol);
                            brokerData.setBrokerName(name.trim());
                            brokerData.setBrokerSymbol(symbol.trim());

                            brokerDataList.add(brokerData);
                        }
                    }
                }
            }
        }
    }

    private static void importExcel(File file, List<FollowVarietyExcelVO> brokerDataList) throws IOException {
        try (InputStream inputStream = new FileInputStream(file);
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
                String stdSymbol = row.getCell(0).getStringCellValue();

                for (int i = 1; i < row.getPhysicalNumberOfCells(); i++) {
                    String[] brokerNameParts = brokerNames.get(i).split("/");
                    String brokerSymbol = row.getCell(i).getStringCellValue();
                    String[] brokerSymbolParts = brokerSymbol.split("/");

                    for (String name : brokerNameParts) {
                        for (String symbol : brokerSymbolParts) {
                            FollowVarietyExcelVO brokerData = new FollowVarietyExcelVO();
                            brokerData.setStdSymbol(stdSymbol);
                            brokerData.setBrokerName(name.trim());
                            brokerData.setBrokerSymbol(symbol.trim());
                            brokerDataList.add(brokerData);
                        }
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        try {
            String filePath ="D:\\code\\Java-CopyTrade\\follow-order-mascontrol\\src\\main\\resources\\template\\品种对应表 - （外汇对_贵金属_能源）.csv"; // 替换为实际的文件路径
            List<FollowVarietyExcelVO> brokerDataList = POI.importByFile(filePath);
            System.out.println("Imported data size: " + brokerDataList);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
