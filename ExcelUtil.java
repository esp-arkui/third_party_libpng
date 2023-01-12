package com.huawei.utils;

import com.huawei.enums.DateEnums;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Objects;

public class ExcelUtil {
    private static final Logger logger = LoggerFactory.getLogger(ExcelUtil.class);

    private static final String XLS = ".xls";

    private static final String XLSX = ".xlsx";

    private static final int FIRST_SHEET = 0;

    public static final String DEFAULT_VALUE = "--";

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat(DateEnums.DAY.getPattern());

    private static HashMap<Class, String[]> ALL_TITLE = new HashMap<>();

    @Value("${servicecomb.uploads.directory}")
    private static String directory;

    public static byte[] workbookToBytes(Workbook workbook, String type) {
        if (Objects.isNull(workbook)) {
            return null;
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            workbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            logger.error(" export {}s To File  error ", type);
            return null;
        }
    }

    /**
     * 获取excel中的sheet数据
     *
     * @param file 文件数据
     * @return Sheet sheet数据
     */
    public static Sheet getExcelSheet(MultipartFile file) {
        Sheet sheet = null;
        if (file == null) {
            return sheet;
        }
        Workbook workBook = ExcelUtil.getWorkBook(file);
        if (workBook != null) {
            sheet = ExcelUtil.getSheet(workBook);
        }
        return sheet;
    }

    /**
     * 获取excel的workbook
     *
     * @param file 文件
     * @return Workbook 工作簿
     */
    public static Workbook getWorkBook(MultipartFile file) {
        Workbook workbook = null;
        String fileName = file.getOriginalFilename();
        InputStream inputStream = null;
        try {
            inputStream = file.getInputStream();
            if (fileName.endsWith(XLS)) {
                workbook = new HSSFWorkbook(inputStream);
            } else if (fileName.endsWith(XLSX)) {
                workbook = new XSSFWorkbook(inputStream);
            }
        } catch (IOException e) {
            logger.error("get workbook fail, fileName is {}", fileName);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    logger.error("close input stream error");
                }
            }
        }
        return workbook;
    }

    /**
     * 获取文件的第一个表格
     *
     * @param workbook 工作簿
     * @return Sheet表格
     */
    public static Sheet getSheet(Workbook workbook) {
        return workbook.getSheetAt(FIRST_SHEET);
    }
}
