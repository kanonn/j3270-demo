package com.example;

import com.github.filipesimoes.j3270.Emulator;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * TN3270 高级示例
 */
public class TN3270Advanced {

    private static final String HOST = "localhost";
    private static final int PORT = 3270;
    private static final String USERNAME = "HERC01";
    private static final String PASSWORD = "CUL8TR";

    // 字段位置
    private static final int USERNAME_ROW = 17;
    private static final int USERNAME_COL = 23;
    private static final int PASSWORD_ROW = 18;
    private static final int PASSWORD_COL = 23;

    private Emulator emulator;
    private BufferedWriter logWriter;

    public TN3270Advanced() {
        try {
            String logFile = "tn3270_advanced_log_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".txt";
            logWriter = new BufferedWriter(new FileWriter(logFile, true));
            log("日志文件: " + logFile);
        } catch (IOException e) {
            System.err.println("无法创建日志文件: " + e.getMessage());
        }
    }

    private void log(String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
        String logMessage = "[" + timestamp + "] " + message;
        System.out.println(logMessage);

        if (logWriter != null) {
            try {
                logWriter.write(logMessage);
                logWriter.newLine();
                logWriter.flush();
            } catch (IOException e) {
                System.err.println("写入日志失败: " + e.getMessage());
            }
        }
    }

    /**
     * 打印格式化的屏幕内容
     */
    private void printScreenFormatted(String title) {
        log("\n" + "=".repeat(80));
        log(title);
        log("=".repeat(80));

        try {
            for (int row = 1; row <= 24; row++) {
                String line = emulator.getText(row, 1, 80);
                if (line != null && !line.trim().isEmpty()) {
                    log(String.format("Row %2d: %s", row, line));
                }
            }
            log("=".repeat(80) + "\n");
        } catch (Exception e) {
            log("获取屏幕内容失败: " + e.getMessage());
        }
    }

    /**
     * 保存屏幕截图到文件
     */
    private void saveScreenshot(String filename) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write("=".repeat(80));
            writer.newLine();
            writer.write("Screen Snapshot - " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            writer.newLine();
            writer.write("=".repeat(80));
            writer.newLine();

            for (int row = 1; row <= 24; row++) {
                String line = emulator.getText(row, 1, 80);
                if (line != null) {
                    writer.write(String.format("Row %2d: %s", row, line));
                    writer.newLine();
                }
            }

            log("屏幕截图已保存: " + filename);
        } catch (Exception e) {
            log("保存屏幕截图失败: " + e.getMessage());
        }
    }

    /**
     * 连接到主机
     */
    public boolean connect() {
        try {
            log("========================================");
            log("开始连接到TN3270主机");
            log("========================================");
            log("主机地址: " + HOST + ":" + PORT);

            emulator = new Emulator();

            // 可选: 显示终端窗口(调试用)
            // emulator.setVisible(true);

            emulator.start();
            log("模拟器启动成功");

            boolean connected = emulator.connect(HOST + ":" + PORT);
            if (!connected) {
                log("连接失败");
                return false;
            }

            log("连接建立成功!");

            emulator.waitField(10);
            log("屏幕就绪");

            printScreenFormatted("初始登录屏幕");
            saveScreenshot("screen_01_initial.txt");

            return true;
        } catch (IOException | TimeoutException e) {
            log("连接失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 高级登录
     */
    public boolean advancedLogin() {
        try {
            log("\n========================================");
            log("开始登录流程");
            log("========================================");

            log("步骤1: 等待输入字段就绪");
            emulator.waitField(5);

            log("步骤2: 输入用户名");
            log("        位置: 行=" + USERNAME_ROW + ", 列=" + USERNAME_COL);
            log("        内容: " + USERNAME);
            emulator.fillField(USERNAME_ROW, USERNAME_COL, USERNAME);
            Thread.sleep(500);
            printScreenFormatted("输入用户名后");

            log("步骤3: 输入密码");
            log("        位置: 行=" + PASSWORD_ROW + ", 列=" + PASSWORD_COL);
            log("        内容: " + "*".repeat(PASSWORD.length()));
            emulator.fillField(PASSWORD_ROW, PASSWORD_COL, PASSWORD);
            Thread.sleep(500);
            printScreenFormatted("输入密码后");

            log("步骤4: 提交登录");
            emulator.sendEnter();

            log("步骤5: 等待登录响应");
            boolean ready = emulator.waitField(10);
            if (!ready) {
                log("⚠️ 等待响应超时");
            }

            printScreenFormatted("登录响应屏幕");
            saveScreenshot("screen_02_login_response.txt");

            // 检查错误
            String statusLine = emulator.getText(24, 1, 80);
            if (statusLine != null) {
                String status = statusLine.toLowerCase();
                if (status.contains("error") || status.contains("invalid")) {
                    log("❌ 登录失败: " + statusLine);
                    return false;
                }
            }

            log("✅ 登录成功!");
            return true;

        } catch (Exception e) {
            log("登录过程出错: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 演示所有读取方法
     */
    public void demonstrateReadMethods() {
        log("\n========================================");
        log("演示屏幕读取方法");
        log("========================================");

        // 1. getText - 读取单行文本
        log("\n1. getText(row, col, length) - 读取单行指定长度文本");
        String text1 = emulator.getText(1, 1, 20);
        log("   结果: \"" + text1 + "\"");

        // 2. getText - 读取多行文本区域
        log("\n2. getText(row1, col1, row2, col2) - 读取矩形区域");
        List<String> area = emulator.getText(1, 1, 3, 40);
        if (area != null) {
            for (int i = 0; i < area.size(); i++) {
                log("   行" + (i+1) + ": \"" + area.get(i) + "\"");
            }
        }

        // 3. getTextInterval - 读取单行的一段
        log("\n3. getTextInterval(row, col1, col2) - 读取单行区间");
        String interval = emulator.getTextInterval(1, 1, 40);
        log("   结果: \"" + interval + "\"");

        // 4. containsText - 检查是否包含文本
        log("\n4. containsText(row, col, text) - 检查文本是否匹配");
        boolean contains = emulator.containsText(1, 1, "HER");
        log("   检查位置(1,1)是否为\"HER\": " + contains);
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        try {
            if (emulator != null) {
                log("\n========================================");
                log("断开连接");
                log("========================================");

                printScreenFormatted("断开前最终屏幕");
                saveScreenshot("screen_99_final.txt");

                if (emulator.isConnected()) {
                    emulator.disconnect();
                    log("连接已断开");
                }

                emulator.close();
                log("模拟器已关闭");
            }
        } catch (Exception e) {
            log("关闭时出错: " + e.getMessage());
        } finally {
            if (logWriter != null) {
                try {
                    logWriter.close();
                } catch (IOException e) {
                    System.err.println("关闭日志文件失败: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 主程序
     */
    public static void main(String[] args) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("TN3270 高级示例程序");
        System.out.println("=".repeat(80) + "\n");

        TN3270Advanced demo = new TN3270Advanced();

        try {
            // 1. 连接
            if (!demo.connect()) {
                System.err.println("\n❌ 连接失败,程序终止");
                return;
            }

            // 2. 登录
            if (!demo.advancedLogin()) {
                System.err.println("\n❌ 登录失败,程序终止");
                return;
            }

            // 3. 演示各种读取方法
            demo.demonstrateReadMethods();

            // 等待观察
            Thread.sleep(3000);

            System.out.println("\n✅ 所有操作完成!");

        } catch (Exception e) {
            System.err.println("\n❌ 程序执行出错: " + e.getMessage());
            e.printStackTrace();
        } finally {
            demo.disconnect();
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("程序执行完毕! 请查看日志文件和屏幕截图。");
        System.out.println("=".repeat(80) + "\n");
    }
}