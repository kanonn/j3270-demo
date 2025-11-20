package com.example;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * TK4- MVS TSO 自动化程序
 * 功能: 自动登录、执行命令、自动登出
 */
public class TN3270Demo {

    // ============ 连接配置 ============
    private static final String HOST = "localhost";
    private static final int PORT = 3270;
    private static final int SCRIPT_PORT = 6001;
    private static final String TERMINAL_MODEL = "3278-2";

    // ============ ws3270配置 ============
    private static final String WS3270_DIR = "E:\\01_work\\003-anems-sample\\wc3270-4.4ga6-noinstall-64";
    private static final String WS3270_PATH = WS3270_DIR + "\\ws3270.bat";
    private static final String X3270IF_PATH = WS3270_DIR + "\\x3270if.exe";

    // ============ 日志配置 ============
    private static final String LOG_FILE = "tn3270_complete.txt";

    private Process ws3270Process;
    private BufferedWriter logWriter;

    public TN3270Demo() {
        try {
            logWriter = new BufferedWriter(new FileWriter(LOG_FILE, false));
        } catch (IOException e) {
            System.err.println("无法创建日志文件");
        }
    }

    private void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        String logMessage = "[" + timestamp + "] " + message;
        System.out.println(logMessage);

        if (logWriter != null) {
            try {
                logWriter.write(logMessage);
                logWriter.newLine();
                logWriter.flush();
            } catch (IOException e) {
                // 忽略
            }
        }
    }

    /**
     * 执行x3270if命令
     */
    private String executeX3270if(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(X3270IF_PATH, "-t", String.valueOf(SCRIPT_PORT), command);
            Process proc = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            proc.waitFor();
            return output.toString().trim();

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * 查找输入字段位置
     */
    private int[] findInputField(String screenContent) {
        String[] lines = screenContent.split("\n");

        for (int row = 0; row < lines.length; row++) {
            String line = lines[row];
            String upperLine = line.toUpperCase();

            // 查找 "Logon ===>"
            if (upperLine.contains("LOGON")) {
                int pos = upperLine.indexOf("LOGON");
                int arrowPos = line.indexOf("===>", pos);

                if (arrowPos >= 0) {
                    int inputCol = arrowPos + 5;

                    while (inputCol < line.length() && line.charAt(inputCol) == ' ') {
                        inputCol++;
                    }

                    if (inputCol >= line.length()) {
                        inputCol = arrowPos + 5;
                    }

                    log("  找到Logon字段:");
                    log("    行" + (row + 1) + ": " + line);
                    log("    输入位置: 行=" + (row + 1) + ", 列=" + (inputCol + 1));

                    return new int[]{row + 1, inputCol + 1};
                }
            }

            // 查找 "ENTER CURRENT PASSWORD" (密码提示)
            if (upperLine.contains("ENTER CURRENT PASSWORD") ||
                    (upperLine.contains("PASSWORD") && line.contains("-"))) {

                int dashPos = line.lastIndexOf("-");
                if (dashPos >= 0 && dashPos < line.length() - 1) {
                    log("  找到密码输入字段:");
                    log("    行" + (row + 1) + ": " + line);
                    log("    输入位置: 行=" + (row + 1) + ", 列=" + (dashPos + 2));

                    return new int[]{row + 1, dashPos + 2};
                }
            }
        }

        return null;
    }

    /**
     * 连接到TK4-
     */
    public boolean connect() {
        try {
            System.setProperty("java.net.preferIPv4Stack", "true");

            log("==============================================================");
            log("TK4- MVS TSO 自动化程序");
            log("==============================================================");
            log("开始时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            log("");

            // 1. 启动ws3270
            log("[1] 启动ws3270");
            log("--------------------------------------------------------------");

            ProcessBuilder pb = new ProcessBuilder(
                    WS3270_PATH,
                    "-scriptport", "localhost:" + SCRIPT_PORT,
                    "-model", TERMINAL_MODEL
            );

            pb.redirectErrorStream(true);
            ws3270Process = pb.start();

            new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(ws3270Process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log("    [ws3270] " + line);
                    }
                } catch (IOException e) {
                    // 忽略
                }
            }).start();

            log("  等待ws3270启动...");
            Thread.sleep(5000);

            if (!ws3270Process.isAlive()) {
                log("  ✗ ws3270进程已退出");
                return false;
            }
            log("  ✓ ws3270运行中");
            log("");

            // 2. 连接到TK4-
            log("[2] 连接到TK4-");
            log("--------------------------------------------------------------");
            log("  目标: " + HOST + ":" + PORT);

            String connectResult = executeX3270if("Connect(" + HOST + ":" + PORT + ")");
            log("  Connect: " + (connectResult.isEmpty() ? "(无输出)" : connectResult));

            log("  等待连接建立...");
            Thread.sleep(3000);

            String connState = executeX3270if("Query(ConnectionState)");
            log("  连接状态: " + connState);

            if (!connState.startsWith("connected")) {
                log("  ✗ 连接失败");
                return false;
            }
            log("  ✓ 已连接");

            // 读取TK4-启动画面
            String startScreen = executeX3270if("Ascii");
            if (startScreen.contains("TK4")) {
                log("  ✓ TK4-启动画面已显示");
            }

            // 3. 发送Clear进入TSO登录
            log("\n[3] 进入TSO登录界面");
            log("--------------------------------------------------------------");
            log("  发送Clear命令...");

            executeX3270if("Clear");
            Thread.sleep(3000);

            String screenContent = executeX3270if("Ascii");

            if (!screenContent.toUpperCase().contains("LOGON")) {
                log("  ✗ 未检测到TSO登录屏幕");
                return false;
            }

            log("  ✓ TSO登录屏幕就绪");
            log("");

            // 4. 显示登录屏幕
            log("[4] TSO登录屏幕内容");
            log("--------------------------------------------------------------");

            String[] lines = screenContent.split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (!lines[i].trim().isEmpty()) {
                    log(String.format("  Row %2d: %s", i + 1, lines[i]));
                }
            }

            log("");
            log("✓ 连接成功!");

            return true;

        } catch (Exception e) {
            log("");
            log("✗ 连接失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 自动登录
     */
    public boolean autoLogin(String username, String password) {
        try {
            log("");
            log("==============================================================");
            log("自动登录");
            log("==============================================================");

            // 1. Reset键盘
            log("\n[1] Reset解锁键盘");
            log("--------------------------------------------------------------");
            executeX3270if("Reset");
            Thread.sleep(500);

            String keyboardLock = executeX3270if("Query(KeyboardLock)");
            log("  键盘状态: " + keyboardLock);

            // 2. 读取登录屏幕
            log("\n[2] 读取登录屏幕");
            log("--------------------------------------------------------------");
            String screenContent = executeX3270if("Ascii");

            // 3. 查找并输入用户名
            log("\n[3] 输入用户名");
            log("--------------------------------------------------------------");

            int[] userPos = findInputField(screenContent);

            if (userPos == null) {
                log("  ✗ 未找到Logon字段");
                return false;
            }

            log("  用户名: " + username);
            log("  移动光标到: 行=" + userPos[0] + ", 列=" + userPos[1]);

            executeX3270if("MoveCursor(" + userPos[0] + "," + userPos[1] + ")");
            Thread.sleep(300);

            String stringResult = executeX3270if("String(\"" + username + "\")");

            if (stringResult.contains("error") || stringResult.contains("locked")) {
                log("  ✗ 输入失败: " + stringResult);
                return false;
            }

            log("  ✓ 用户名已输入");
            Thread.sleep(500);

            // 4. 提交用户名
            log("\n[4] 提交用户名");
            log("--------------------------------------------------------------");

            executeX3270if("Enter");
            log("  Enter已发送");

            log("  等待密码提示...");
            Thread.sleep(5000);

            // 5. 读取密码提示屏幕
            log("\n[5] 读取密码提示屏幕");
            log("--------------------------------------------------------------");

            String passwordScreen = executeX3270if("Ascii");
            String[] passLines = passwordScreen.split("\n");

            for (int i = 0; i < Math.min(passLines.length, 10); i++) {
                if (!passLines[i].trim().isEmpty()) {
                    log(String.format("  Row %2d: %s", i + 1, passLines[i]));
                }
            }

            // 6. 输入密码
            if (passwordScreen.toUpperCase().contains("PASSWORD")) {
                log("\n[6] 输入密码");
                log("--------------------------------------------------------------");

                executeX3270if("Reset");
                Thread.sleep(500);

                int[] passPos = findInputField(passwordScreen);

                if (passPos != null) {
                    log("  密码: ******");
                    log("  移动光标到: 行=" + passPos[0] + ", 列=" + passPos[1]);

                    executeX3270if("MoveCursor(" + passPos[0] + "," + passPos[1] + ")");
                    Thread.sleep(300);

                    executeX3270if("String(\"" + password + "\")");
                    log("  ✓ 密码已输入");
                    Thread.sleep(500);

                    log("\n[7] 提交密码");
                    log("--------------------------------------------------------------");

                    executeX3270if("Enter");
                    log("  Enter已发送");

                    log("  等待登录完成...");
                    Thread.sleep(5000);
                }
            }

            // 8. 读取登录后屏幕
            log("\n[8] 登录后屏幕");
            log("--------------------------------------------------------------");

            String finalScreen = executeX3270if("Ascii");
            String[] finalLines = finalScreen.split("\n");

            for (int i = 0; i < Math.min(finalLines.length, 15); i++) {
                if (!finalLines[i].trim().isEmpty()) {
                    log(String.format("  Row %2d: %s", i + 1, finalLines[i]));
                }
            }

            // 9. 检查登录结果
            if (finalScreen.toUpperCase().contains("WELCOME") ||
                    finalScreen.toUpperCase().contains("TSO") ||
                    finalScreen.toUpperCase().contains("READY") ||
                    finalScreen.toUpperCase().contains("OPTION")) {
                log("");
                log("✓ 登录成功!");
                return true;
            } else {
                log("");
                log("⚠ 登录流程完成,请查看屏幕内容确认");
                return true;
            }

        } catch (Exception e) {
            log("");
            log("✗ 登录失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 登出TSO
     */
    public boolean logout() {
        try {
            log("");
            log("==============================================================");
            log("登出TSO");
            log("==============================================================");

            // 1. 读取当前屏幕
            log("\n[1] 读取当前屏幕");
            log("--------------------------------------------------------------");
            String currentScreen = executeX3270if("Ascii");

            String[] lines = currentScreen.split("\n");
            for (int i = 0; i < Math.min(lines.length, 10); i++) {
                if (!lines[i].trim().isEmpty()) {
                    log(String.format("  Row %2d: %s", i + 1, lines[i]));
                }
            }

            // 2. 如果在应用菜单,先退出到READY
            if (currentScreen.toUpperCase().contains("OPTION") ||
                    currentScreen.toUpperCase().contains("TSO APPLICATIONS")) {

                log("\n[2] 从TSO应用菜单退出");
                log("--------------------------------------------------------------");

                executeX3270if("Reset");
                Thread.sleep(500);

                log("  使用Tab定位到输入字段");
                executeX3270if("Tab");
                Thread.sleep(300);

                log("  输入: X");
                executeX3270if("String(\"X\")");
                Thread.sleep(300);

                log("  提交");
                executeX3270if("Enter");

                log("  等待退出到READY提示符...");
                Thread.sleep(3000);

                String readyScreen = executeX3270if("Ascii");
                log("\n退出后屏幕:");
                log("--------------------------------------------------------------");
                String[] readyLines = readyScreen.split("\n");
                for (int i = 0; i < Math.min(readyLines.length, 10); i++) {
                    if (!readyLines[i].trim().isEmpty()) {
                        log(String.format("  Row %2d: %s", i + 1, readyLines[i]));
                    }
                }

                if (readyScreen.toUpperCase().contains("READY")) {
                    log("  ✓ 已到达READY提示符");
                }
            }

            // 3. 发送LOGOFF命令
            log("\n[3] 发送LOGOFF命令");
            log("--------------------------------------------------------------");

            executeX3270if("Reset");
            Thread.sleep(500);

            log("  输入: LOGOFF");
            executeX3270if("String(\"LOGOFF\")");
            Thread.sleep(300);

            log("  提交");
            executeX3270if("Enter");

            log("  等待登出完成...");
            Thread.sleep(5000);

            // 4. 读取登出后屏幕
            log("\n[4] 登出后屏幕");
            log("--------------------------------------------------------------");

            String logoutScreen = executeX3270if("Ascii");
            String[] logoutLines = logoutScreen.split("\n");

            for (int i = 0; i < Math.min(logoutLines.length, 15); i++) {
                if (!logoutLines[i].trim().isEmpty()) {
                    log(String.format("  Row %2d: %s", i + 1, logoutLines[i]));
                }
            }

            if (logoutScreen.toUpperCase().contains("LOGON")) {
                log("  ✓ 已返回登录屏幕");
            }

            // 5. 断开连接
            log("\n[5] 断开连接");
            log("--------------------------------------------------------------");

            executeX3270if("Disconnect");
            Thread.sleep(1000);

            String connState = executeX3270if("Query(ConnectionState)");
            log("  连接状态: " + connState);

            if (connState.equals("not-connected")) {
                log("  ✓ 已断开连接");
            }

            log("");
            log("✓ 登出完成!");

            return true;

        } catch (Exception e) {
            log("");
            log("✗ 登出失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 关闭ws3270进程
     */
    public void disconnect() {
        log("");
        log("==============================================================");
        log("关闭ws3270");
        log("==============================================================");

        try {
            if (ws3270Process != null && ws3270Process.isAlive()) {
                ws3270Process.destroyForcibly();
                ws3270Process.waitFor();
                log("  ✓ ws3270进程已终止");
            }
        } catch (Exception e) {
            log("  关闭ws3270时出错: " + e.getMessage());
        } finally {
            if (logWriter != null) {
                try {
                    log("");
                    log("结束时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                    logWriter.close();
                } catch (IOException e) {
                    // 忽略
                }
            }
        }
    }

    /**
     * 主程序
     */
    public static void main(String[] args) {
        System.out.println("\n==============================================================");
        System.out.println("TK4- MVS TSO 完整自动化程序");
        System.out.println("==============================================================\n");
        System.out.println("功能:");
        System.out.println("  1. 自动连接到TK4- MVS系统");
        System.out.println("  2. 自动登录TSO");
        System.out.println("  3. 自动登出");
        System.out.println();

        TN3270Demo demo = new TN3270Demo();

        try {
            // 1. 连接
            System.out.println("步骤1: 连接到TK4-...\n");
            if (!demo.connect()) {
                System.err.println("\n❌ 连接失败");
                return;
            }

            System.out.println("\n✓ 连接成功!");

            // 2. 登录
            System.out.println("\n步骤2: 自动登录...\n");
            if (!demo.autoLogin("HERC01", "CUL8TR")) {
                System.err.println("\n❌ 登录失败");
                return;
            }

            System.out.println("\n✓ 登录成功!");
            System.out.println("\n已成功登录到TSO系统!");
            System.out.println("可以在此执行其他TSO命令...");

            // 3. 等待(模拟实际使用)
            System.out.println("\n程序将在30秒后自动登出...");
            for (int i = 30; i > 0; i--) {
                System.out.print("\r剩余 " + i + " 秒... ");
                Thread.sleep(1000);
            }
            System.out.println();

            // 4. 登出
            System.out.println("\n步骤3: 自动登出...\n");
            demo.logout();

            System.out.println("\n✓ 登出成功!");

        } catch (Exception e) {
            System.err.println("\n❌ 程序错误: " + e.getMessage());
            e.printStackTrace();
        } finally {
            demo.disconnect();
        }

        System.out.println("\n==============================================================");
        System.out.println("程序执行完毕!");
        System.out.println("详细日志: " + LOG_FILE);
        System.out.println("==============================================================\n");
    }
}