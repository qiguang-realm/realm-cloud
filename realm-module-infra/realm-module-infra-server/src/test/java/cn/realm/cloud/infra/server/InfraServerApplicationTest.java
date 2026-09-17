package cn.realm.cloud.infra.server;

import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class InfraServerApplicationTest {

    private static final Logger logger = LoggerFactory.getLogger(InfraServerApplicationTest.class);

    /**
     * https://stackoverflow.com/questions/157944/create-arraylist-from-array
     * <p>
     * Since Java 8 there is an easier way to transform:
     *
     * @param array
     * @param <T>
     * @return
     */
    public static <T> List<T> fromArray(T[] array) {
        return Arrays.stream(array).collect(toList());
    }

    String[] array = {"a", "b", "c"};

    @Test
    public void fromArrayTest() {
        logger.info(JSON.toJSONString(fromArray(array)));
//        System.out.println(JSON.toJSONString(fromArray(array)));
    }

    @Test
    public void convertTimestampTest() {
        String timestampStr = "1748248519000";
        String formattedDate = convertTimestamp(timestampStr, "yyyy-MM-dd HH:mm:ss");
        System.out.println("格式化后的日期: " + formattedDate);
    }

    /**
     * 自定义输出格式
     *
     * @param timestampStr 时间戳字符串
     * @param pattern      日期格式模式
     * @return 格式化后的日期时间字符串
     */
    public static String convertTimestamp(String timestampStr, String pattern) {
        long timestamp = Long.parseLong(timestampStr);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
        ).format(formatter);
    }

    @Test
    public void readTxtToSqlTest() throws IOException {

        // 文件路径配置
        String inputFilePath = "D:\\qiguang\\workspace\\git_workspace\\java-realm\\realm-module-system\\realm-module-system-server\\src\\main\\resources\\static\\input.txt";
        String outputFilePath = "D:\\qiguang\\workspace\\git_workspace\\java-realm\\realm-module-system\\realm-module-system-server\\src\\main\\resources\\static\\output.txt";

        try (BufferedReader br = new BufferedReader(new FileReader(inputFilePath));
             BufferedWriter bw = new BufferedWriter(new FileWriter(outputFilePath))) {

            // 初始化统计变量
            int totalLines = 0;       // 文件总行数
            int skippedLines = 0;     // 总跳过行数
            int processedLines = 0;   // 成功处理行数
            int countEmptyLines = 0;  // 空行数量
            int countFormatErrors = 0;// 格式错误行数
            int countEmptyValues = 0; // 空值行数

            logger.info("开始处理文件: {}", inputFilePath);

            String line;
            while ((line = br.readLine()) != null) {
                totalLines++;

                logger.debug("Line {}: [{}]", totalLines, line);  // 使用 logger 记录显示实际读取内容

                line = line.trim();

                // 处理空行情况
                if (line.isEmpty()) {
                    logger.debug("跳过空行，行号: {}", totalLines);
                    skippedLines++;
                    countEmptyLines++;
                    continue;
                }

                // 移除制表符并按逗号分割字段
                String[] fields = line.replace("\t", "").split(",");

                // 校验字段数量
                if (fields.length < 2) {
                    logger.warn("行格式错误(字段不足)，行号: {}，内容: {}", totalLines, line);
                    skippedLines++;
                    countFormatErrors++;
                    continue;
                }

                String plateNo = fields[0].trim();
                String fuelType = fields[1].trim();

                // 校验关键字段非空
                if (plateNo.isEmpty() || fuelType.isEmpty()) {
                    logger.warn("关键字段为空，行号: {}，内容: {}", totalLines, line);
                    skippedLines++;
                    countEmptyValues++;
                    continue;
                }

                // 生成SQL语句  UPDATE KYC_VEHICLE SET FUEL_TYPE = '' WHERE LICENSE_PLATE = ''
                String sqlResult = String.format(
                        "UPDATE KYC_VEHICLE SET FUEL_TYPE = '%s' WHERE LICENSE_PLATE = '%s';",
                        fuelType, plateNo);

                // 写入输出文件
                bw.write(sqlResult);
                bw.newLine();
                processedLines++;

                logger.debug("成功处理行，行号: {}，车牌号: {}", totalLines, plateNo);
            }

            // 输出处理结果统计
            logger.info("文件处理完成统计结果:");
            logger.info("文件总行数: {}", totalLines);
            logger.info("跳过行数: {} (空行: {}, 格式错误: {}, 空值: {})",
                    skippedLines, countEmptyLines, countFormatErrors, countEmptyValues);
            logger.info("成功生成SQL语句行数: {}", processedLines);
            logger.info("输出文件已生成: {}", outputFilePath);

        } catch (IOException e) {
            logger.error("文件处理过程中发生IO异常", e);
            throw e;
        }
    }


}
