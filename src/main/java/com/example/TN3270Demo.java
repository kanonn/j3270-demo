package com.example;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * TK4- MVS TSO 自动化程序 (修正版)
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
    private static final String LOG_FILE = "tn3270_debug.txt";

    // ⭐ 坐标校准偏移量
    private static final int ROW_OFFSET = -1;
    private static final int COL_OFFSET = -1;

    // ⭐⭐⭐ 密码字段固定位置(根据手动测试成功的坐标) ⭐⭐⭐
    private static final int PASSWORD_ROW = 1;
    private static final int PASSWORD_COL = 24;

    private boolean debugMode = false;
    private Process ws3270Process;
    private BufferedWriter logWriter;
    private int commandCounter = 0;

    public TN3270Demo() {
        this(false);
    }

    public TN3270Demo(boolean debugMode) {
        this.debugMode = debugMode;
        try {
            logWriter = new BufferedWriter(new FileWriter(LOG_FILE, false));
            log("==============================================================");
            log("调试模式: " + (debugMode ? "开启" : "关闭"));
            log("==============================================================");
            log("");
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

    private String executeX3270if(String command) {
        return executeX3270if(command, true);
    }

    private String executeX3270if(String command, boolean autoDebug) {
        commandCounter++;

        try {
            log(">>> [命令 #" + commandCounter + "] " + command);

            ProcessBuilder pb = new ProcessBuilder(X3270IF_PATH, "-t", String.valueOf(SCRIPT_PORT), command);
            Process proc = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            proc.waitFor();
            String result = output.toString().trim();

            if (!result.isEmpty() && !command.startsWith("Ascii")) {
                log("<<< [返回 #" + commandCounter + "] " +
                        (result.length() > 100 ? result.substring(0, 100) + "..." : result));
            }

            if (debugMode && autoDebug && !command.startsWith("Ascii") && !command.startsWith("Query")) {
                Thread.sleep(300);
                dumpScreen("执行 [" + command + "] 后");
            }

            return result;

        } catch (Exception e) {
            log("!!! [错误 #" + commandCounter + "] " + e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    private void dumpScreen(String title) {
        try {
            String screen = executeX3270if("Ascii", false);

            log("");
            log("┌─────────────────────────────────────────────────────────────┐");
            log("│ 屏幕快照: " + title);
            log("├─────────────────────────────────────────────────────────────┤");

            String[] lines = screen.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.length() < 80) {
                    line = line + " ".repeat(80 - line.length());
                }
                log(String.format("│%2d│ %s", i + 1, line.substring(0, Math.min(80, line.length()))));
            }

            log("└─────────────────────────────────────────────────────────────┘");
            log("");

        } catch (Exception e) {
            log("!!! 无法读取屏幕: " + e.getMessage());
        }
    }

    public void showScreen(String title) {
        dumpScreen(title);
    }

    /**
     * 查找Logon字段位置
     */
    private int[] findLogonField(String screenContent) {
        String[] lines = screenContent.split("\n");

        log("  开始查找Logon字段,共" + lines.length + "行");

        for (int row = 0; row < lines.length; row++) {
            String line = lines[row];
            String upperLine = line.toUpperCase();

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

                    int finalRow = (row + 1) + ROW_OFFSET;
                    int finalCol = (inputCol + 1) + COL_OFFSET;

                    log("  ✓ 找到Logon字段:");
                    log("    数组索引: row=" + row + ", arrowPos=" + arrowPos + ", inputCol=" + inputCol);
                    log("    原始坐标: 行=" + (row + 1) + ", 列=" + (inputCol + 1));
                    log("    校准后坐标: 行=" + finalRow + ", 列=" + finalCol);
                    log("    行内容: " + line);

                    return new int[]{finalRow, finalCol};
                }
            }
        }

        log("  ✗ 未找到Logon字段");
        return null;
    }

    /**
     * 分析密码字段位置(仅用于调试)
     */
    private void analyzePasswordField(String screenContent) {
        String[] lines = screenContent.split("\n");

        log("  分析密码字段位置:");
        log("  ================");

        for (int row = 0; row < lines.length; row++) {
            String line = lines[row];
            String upperLine = line.toUpperCase();

            if (upperLine.contains("PASSWORD")) {
                log("  第" + (row + 1) + "行包含PASSWORD:");
                log("    原始内容: [" + line + "]");
                log("    长度: " + line.length());

                // 显示每个字符的位置
                log("    字符位置分析:");
                for (int i = 0; i < Math.min(line.length(), 50); i++) {
                    char c = line.charAt(i);
                    if (c != ' ') {
                        log("      位置" + i + "(x3270=" + (i+1) + "): '" + c + "'");
                    }
                }

                // 查找 "-" 的位置
                int dashPos = line.lastIndexOf("-");
                if (dashPos >= 0) {
                    log("    '-' 的位置: 数组索引=" + dashPos + ", x3270坐标=" + (dashPos + 1));
                    log("    '-' 后一位: 数组索引=" + (dashPos + 1) + ", x3270坐标=" + (dashPos + 2));
                }

                log("  根据手动测试成功的坐标:");
                log("    成功位置: 行=1, 列=24");
                log("    对应数组: row=0, col=23");

                // 检查第23列是什么
                if (line.length() > 23) {
                    log("    第23列(数组索引22)的字符: '" + line.charAt(22) + "'");
                    log("    第24列(数组索引23)的字符: '" + line.charAt(23) + "'");
                }
            }
        }
    }

    public boolean connect() {
        try {
            System.setProperty("java.net.preferIPv4Stack", "true");

            log("==============================================================");
            log("开始连接流程");
            log("==============================================================");
            log("时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
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

            executeX3270if("Connect(" + HOST + ":" + PORT + ")");
            Thread.sleep(3000);

            String connState = executeX3270if("Query(ConnectionState)", false);
            log("  连接状态: " + connState);

            if (!connState.startsWith("connected")) {
                log("  ✗ 连接失败");
                return false;
            }

            // 3. 进入TSO登录
            log("\n[3] 进入TSO登录界面");
            log("--------------------------------------------------------------");

            executeX3270if("Clear");
            Thread.sleep(3000);

            String screenContent = executeX3270if("Ascii", false);

            if (!screenContent.toUpperCase().contains("LOGON")) {
                log("  ✗ 未检测到TSO登录屏幕");
                return false;
            }

            log("  ✓ TSO登录屏幕就绪");

            if (debugMode) {
                dumpScreen("TSO登录屏幕");
            }

            return true;

        } catch (Exception e) {
            log("✗ 连接失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean autoLogin(String username, String password) {
        try {
            log("");
            log("==============================================================");
            log("开始登录流程");
            log("==============================================================");

            // 1. Reset键盘
            log("\n[1] Reset解锁键盘");
            log("--------------------------------------------------------------");
            executeX3270if("Reset");
            Thread.sleep(500);

            // 2. 读取并查找Logon字段
            log("\n[2] 查找Logon字段");
            log("--------------------------------------------------------------");
            String screenContent = executeX3270if("Ascii", false);

            int[] userPos = findLogonField(screenContent);

            if (userPos == null) {
                log("  ✗ 未找到Logon字段");
                return false;
            }

            // 3. 输入用户名
            log("\n[3] 输入用户名");
            log("--------------------------------------------------------------");
            log("  用户名: " + username);
            log("  位置: 行=" + userPos[0] + ", 列=" + userPos[1]);

            executeX3270if("MoveCursor(" + userPos[0] + "," + userPos[1] + ")");
            Thread.sleep(300);

            executeX3270if("String(\"" + username + "\")");
            log("  ✓ 用户名已输入");
            Thread.sleep(500);

            // 4. 提交用户名
            log("\n[4] 提交用户名");
            log("--------------------------------------------------------------");

            executeX3270if("Enter");
            log("  等待密码提示...");
            Thread.sleep(5000);

            // 5. 读取密码屏幕并分析
            log("\n[5] 分析密码提示屏幕");
            log("--------------------------------------------------------------");

            String passwordScreen = executeX3270if("Ascii", false);

            if (debugMode) {
                dumpScreen("密码提示屏幕");
            }

            // ⭐ 分析密码字段位置
            analyzePasswordField(passwordScreen);

            // 6. 输入密码(使用固定位置)
            if (passwordScreen.toUpperCase().contains("PASSWORD")) {
                log("\n[6] 输入密码(使用固定位置)");
                log("--------------------------------------------------------------");

                executeX3270if("Reset");
                Thread.sleep(500);

                log("  密码: ******");
                log("  ⭐ 使用固定位置: 行=" + PASSWORD_ROW + ", 列=" + PASSWORD_COL);

                executeX3270if("MoveCursor(" + PASSWORD_ROW + "," + PASSWORD_COL + ")");
                Thread.sleep(300);

                executeX3270if("String(\"" + password + "\")");
                log("  ✓ 密码已输入");
                Thread.sleep(500);

                log("\n[7] 提交密码");
                log("--------------------------------------------------------------");

                executeX3270if("Enter");
                log("  等待登录完成...");
                Thread.sleep(5000);
            }

            // 8. 验证登录结果
            log("\n[8] 验证登录结果");
            log("--------------------------------------------------------------");

            String finalScreen = executeX3270if("Ascii", false);

            if (debugMode) {
                dumpScreen("登录后的屏幕");
            }

            if (finalScreen.toUpperCase().contains("WELCOME") ||
                    finalScreen.toUpperCase().contains("OPTION") ||
                    finalScreen.toUpperCase().contains("READY")) {
                log("  ✓ 登录成功!");
                return true;
            } else if (finalScreen.toUpperCase().contains("INVALID")) {
                log("  ✗ 登录失败: 检测到INVALID错误");
                return false;
            } else {
                log("  ⚠ 登录状态不明确");
                return true;
            }

        } catch (Exception e) {
            log("✗ 登录失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean logout() {
        try {
            log("");
            log("==============================================================");
            log("开始登出流程");
            log("==============================================================");

            String currentScreen = executeX3270if("Ascii", false);

            if (debugMode) {
                dumpScreen("登出前的屏幕");
            }

            // 从应用菜单退出
            if (currentScreen.toUpperCase().contains("OPTION")) {
                log("\n[1] 从应用菜单退出");
                log("--------------------------------------------------------------");

                executeX3270if("Reset");
                Thread.sleep(500);

                executeX3270if("Tab");
                Thread.sleep(300);

                executeX3270if("String(\"X\")");
                Thread.sleep(300);

                executeX3270if("Enter");
                Thread.sleep(3000);

                if (debugMode) {
                    dumpScreen("退出应用菜单后");
                }
            }

            // 发送LOGOFF
            log("\n[2] 发送LOGOFF");
            log("--------------------------------------------------------------");

            executeX3270if("Reset");
            Thread.sleep(500);

            executeX3270if("String(\"LOGOFF\")");
            Thread.sleep(300);

            executeX3270if("Enter");
            Thread.sleep(5000);

            if (debugMode) {
                dumpScreen("LOGOFF后");
            }

            // 断开连接
            log("\n[3] 断开连接");
            log("--------------------------------------------------------------");

            executeX3270if("Disconnect");
            Thread.sleep(1000);

            log("  ✓ 登出完成");
            return true;

        } catch (Exception e) {
            log("✗ 登出失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * ⭐⭐⭐ 改进的关闭方法 ⭐⭐⭐
     */
    public void disconnect() {
        log("");
        log("==============================================================");
        log("关闭ws3270");
        log("==============================================================");

        try {
            // 方法1: 尝试正常关闭
            if (ws3270Process != null && ws3270Process.isAlive()) {
                log("  尝试正常终止进程...");
                ws3270Process.destroy();

                // 等待最多3秒
                boolean exited = ws3270Process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);

                if (exited) {
                    log("  ✓ 进程已正常终止");
                } else {
                    log("  ⚠ 进程未正常终止,尝试强制终止...");
                    ws3270Process.destroyForcibly();
                    ws3270Process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                }
            }

            // 方法2: 使用taskkill确保清理
            log("  使用taskkill清理ws3270进程...");

            ProcessBuilder pb = new ProcessBuilder("taskkill", "/F", "/IM", "ws3270.exe");
            pb.redirectErrorStream(true);
            Process killProc = pb.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(killProc.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log("    [taskkill] " + line);
            }

            killProc.waitFor();

            // 同时清理ws3270_real.exe
            log("  清理ws3270_real.exe进程...");

            pb = new ProcessBuilder("taskkill", "/F", "/IM", "ws3270_real.exe");
            pb.redirectErrorStream(true);
            killProc = pb.start();

            reader = new BufferedReader(
                    new InputStreamReader(killProc.getInputStream()));
            while ((line = reader.readLine()) != null) {
                log("    [taskkill] " + line);
            }

            killProc.waitFor();

            log("  ✓ 所有ws3270进程已清理");

        } catch (Exception e) {
            log("  ⚠ 关闭时出错: " + e.getMessage());
        } finally {
            if (logWriter != null) {
                try {
                    log("");
                    log("总命令数: " + commandCounter);
                    log("结束时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                    logWriter.close();
                } catch (IOException e) {
                    // 忽略
                }
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("\n==============================================================");
        System.out.println("TK4- MVS TSO 自动化程序 (修正版)");
        System.out.println("==============================================================\n");

        boolean debugMode = true;

        System.out.println("调试模式: " + (debugMode ? "开启" : "关闭"));
        System.out.println("密码字段: 使用固定位置 (1,24)");
        System.out.println();

        TN3270Demo demo = new TN3270Demo(debugMode);

        try {
            if (!demo.connect()) {
                System.err.println("\n❌ 连接失败");
                return;
            }

            System.out.println("\n✓ 连接成功!");

            if (!demo.autoLogin("HERC01", "CUL8TR")) {
                System.err.println("\n❌ 登录失败");
                return;
            }

            System.out.println("\n✓ 登录成功!");
            System.out.println("\n程序将在30秒后自动登出...");

            for (int i = 30; i > 0; i--) {
                System.out.print("\r剩余 " + i + " 秒... ");
                Thread.sleep(1000);
            }
            System.out.println();

            demo.logout();
            System.out.println("\n✓ 登出成功!");

        } catch (Exception e) {
            System.err.println("\n❌ 错误: " + e.getMessage());
            e.printStackTrace();
        } finally {
            demo.disconnect();
        }

        System.out.println("\n==============================================================");
        System.out.println("程序完成!");
        System.out.println("详细日志: " + LOG_FILE);
        System.out.println("==============================================================\n");
    }
}