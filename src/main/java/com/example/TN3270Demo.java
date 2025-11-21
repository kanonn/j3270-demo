package com.example;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * TK4- MVS TSO 自动化程序 (可配置版)
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

    // ⭐ 密码字段固定位置
    private static final int PASSWORD_ROW = 1;
    private static final int PASSWORD_COL = 24;

    // ⭐⭐⭐ 控制变量 ⭐⭐⭐
    private boolean debugMode = false;           // 是否开启调试模式
    private boolean onlyShowScreenChanges = false;  // true=只显示变化的屏幕, false=显示所有屏幕

    private Process ws3270Process;
    private BufferedWriter logWriter;
    private int commandCounter = 0;
    private int screenDumpCounter = 0;
    private String lastScreen = "";  // 用于检测屏幕变化

    public TN3270Demo() {
        this(false, false);
    }

    public TN3270Demo(boolean debugMode) {
        this(debugMode, false);
    }

    /**
     * @param debugMode 是否开启调试模式
     * @param onlyShowScreenChanges true=只显示变化的屏幕, false=显示所有屏幕
     */
    public TN3270Demo(boolean debugMode, boolean onlyShowScreenChanges) {
        this.debugMode = debugMode;
        this.onlyShowScreenChanges = onlyShowScreenChanges;
        try {
            logWriter = new BufferedWriter(new FileWriter(LOG_FILE, false));
            log("==============================================================");
            log("配置:");
            log("  调试模式: " + (debugMode ? "开启" : "关闭"));
            log("  屏幕显示: " + (onlyShowScreenChanges ? "仅显示变化" : "显示所有"));
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

    /**
     * ⭐⭐⭐ 执行x3270if命令 (增强版) ⭐⭐⭐
     * @param command 要执行的命令
     * @param showScreen 是否在命令后显示屏幕
     * @return 命令输出
     */
    private String executeX3270if(String command, boolean showScreen) {
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

            // ⭐ 根据参数决定是否显示屏幕
            if (debugMode && showScreen && !command.startsWith("Ascii") && !command.startsWith("Query")) {
                Thread.sleep(500);

                if (onlyShowScreenChanges) {
                    // 只显示变化的屏幕
                    dumpScreenIfChanged("执行 [" + command + "] 后");
                } else {
                    // 显示所有屏幕
                    dumpScreen("执行 [" + command + "] 后");
                }
            }

            return result;

        } catch (Exception e) {
            log("!!! [错误 #" + commandCounter + "] " + e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * 兼容旧代码的方法 - 默认显示屏幕
     */
    private String executeX3270if(String command) {
        return executeX3270if(command, true);
    }

    /**
     * ⭐ 总是显示屏幕快照
     */
    private void dumpScreen(String title) {
        try {
            screenDumpCounter++;
            String screen = executeX3270if("Ascii", false);

            log("");
            log("┌─────────────────────────────────────────────────────────────┐");
            log("│ 屏幕快照 #" + screenDumpCounter + ": " + title);
            log("├─────────────────────────────────────────────────────────────┤");

            String[] lines = screen.split("\n");

            if (lines.length == 0 || screen.trim().isEmpty()) {
                log("│   (屏幕为空)");
            } else {
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    if (line.length() < 80) {
                        line = line + " ".repeat(80 - line.length());
                    }
                    log(String.format("│%2d│ %s", i + 1, line.substring(0, Math.min(80, line.length()))));
                }
            }

            log("└─────────────────────────────────────────────────────────────┘");
            log("");

            lastScreen = screen;  // 更新最后屏幕内容

        } catch (Exception e) {
            log("!!! 无法读取屏幕: " + e.getMessage());
        }
    }

    /**
     * ⭐ 只在屏幕有变化时才显示
     */
    private void dumpScreenIfChanged(String title) {
        try {
            String screen = executeX3270if("Ascii", false);

            if (!screen.equals(lastScreen)) {
                screenDumpCounter++;

                log("");
                log("┌─────────────────────────────────────────────────────────────┐");
                log("│ 屏幕快照 #" + screenDumpCounter + ": " + title + " (有变化)");
                log("├─────────────────────────────────────────────────────────────┤");

                String[] lines = screen.split("\n");

                if (lines.length == 0 || screen.trim().isEmpty()) {
                    log("│   (屏幕为空)");
                } else {
                    for (int i = 0; i < lines.length; i++) {
                        String line = lines[i];
                        if (line.length() < 80) {
                            line = line + " ".repeat(80 - line.length());
                        }
                        log(String.format("│%2d│ %s", i + 1, line.substring(0, Math.min(80, line.length()))));
                    }
                }

                log("└─────────────────────────────────────────────────────────────┘");
                log("");

                lastScreen = screen;
            } else {
                log("  (屏幕无变化,跳过输出)");
            }

        } catch (Exception e) {
            log("!!! 无法读取屏幕: " + e.getMessage());
        }
    }

    private String getCursorPosition() {
        String cursor = executeX3270if("Query(Cursor)", false);
        log("  当前光标位置: " + cursor);
        return cursor;
    }

    private void sendEnter(int waitSeconds) {
        try {
            log("  发送Enter命令");
            executeX3270if("Enter", true);  // ⭐ 明确指定显示屏幕
            log("  等待 " + waitSeconds + " 秒...");
            Thread.sleep(waitSeconds * 1000);
        } catch (InterruptedException e) {
            log("  等待被中断: " + e.getMessage());
        }
    }

    private void tabAndInput(String input, int waitSeconds) {
        try {
            log("  Reset解锁键盘");
            executeX3270if("Reset", false);  // ⭐ Reset不显示屏幕
            Thread.sleep(300);

            log("  使用Tab定位到输入字段");
            executeX3270if("Tab", false);  // ⭐ Tab不显示屏幕
            Thread.sleep(300);

            getCursorPosition();

            log("  输入: " + input);
            executeX3270if("String(\"" + input + "\")", false);  // ⭐ 输入不显示屏幕
            Thread.sleep(300);

            sendEnter(waitSeconds);  // Enter会显示屏幕

        } catch (InterruptedException e) {
            log("  输入失败: " + e.getMessage());
        }
    }

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
                    log("    校准后坐标: 行=" + finalRow + ", 列=" + finalCol);

                    return new int[]{finalRow, finalCol};
                }
            }
        }

        log("  ✗ 未找到Logon字段");
        return null;
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
            log("  目标: " + HOST + ":" + PORT);

            executeX3270if("Connect(" + HOST + ":" + PORT + ")", true);

            log("  等待连接建立...");
            Thread.sleep(3000);

            String connState = executeX3270if("Query(ConnectionState)", false);
            log("  连接状态: " + connState);

            if (!connState.startsWith("connected")) {
                log("  ✗ 连接失败");
                return false;
            }
            log("  ✓ 已连接");

            // 3. 读取初始屏幕并检查LOGON
            log("\n[3] 检查登录屏幕");
            log("--------------------------------------------------------------");

            dumpScreen("连接后的初始屏幕");

            String screenContent = executeX3270if("Ascii", false);

            // 如果没有LOGON,执行Clear和Reset
            if (!screenContent.toUpperCase().contains("LOGON")) {
                log("  未检测到LOGON字段,执行Clear和Reset");
                log("");

                log("  执行Clear:");
                executeX3270if("Clear", true);
                Thread.sleep(2000);

                log("  执行Reset:");
                executeX3270if("Reset", true);
                Thread.sleep(2000);

                // 再次检查
                screenContent = executeX3270if("Ascii", false);

                if (!screenContent.toUpperCase().contains("LOGON")) {
                    log("  ⚠ 仍未检测到LOGON字段");
                    log("  ⚠ 但继续尝试登录流程");
                } else {
                    log("  ✓ Clear和Reset后检测到LOGON字段");
                }
            } else {
                log("  ✓ 已检测到LOGON字段");
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

    public boolean autoLogin(String username, String password) {
        try {
            log("");
            log("==============================================================");
            log("开始登录流程");
            log("==============================================================");

            // 1. Reset键盘
            log("\n[1] Reset解锁键盘");
            log("--------------------------------------------------------------");
            executeX3270if("Reset", true);
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

            executeX3270if("MoveCursor(" + userPos[0] + "," + userPos[1] + ")", false);
            Thread.sleep(300);

            executeX3270if("String(\"" + username + "\")", true);  // ⭐ 输入用户名后显示
            log("  ✓ 用户名已输入");
            Thread.sleep(500);

            // 4. 提交用户名
            log("\n[4] 提交用户名");
            log("--------------------------------------------------------------");

            executeX3270if("Enter", true);  // ⭐ Enter后显示
            log("  等待密码提示...");
            Thread.sleep(5000);

            // 5. 输入密码
            String passwordScreen = executeX3270if("Ascii", false);

            if (passwordScreen.toUpperCase().contains("PASSWORD")) {
                log("\n[5] 输入密码");
                log("--------------------------------------------------------------");

                executeX3270if("Reset", false);
                Thread.sleep(500);

                log("  密码: ******");
                log("  使用固定位置: 行=" + PASSWORD_ROW + ", 列=" + PASSWORD_COL);

                executeX3270if("MoveCursor(" + PASSWORD_ROW + "," + PASSWORD_COL + ")", false);
                Thread.sleep(300);

                executeX3270if("String(\"" + password + "\")", true);  // ⭐ 输入密码后显示
                log("  ✓ 密码已输入");
                Thread.sleep(500);

                log("\n[6] 提交密码");
                log("--------------------------------------------------------------");

                executeX3270if("Enter", true);  // ⭐ Enter后显示
                log("  等待登录完成...");
                Thread.sleep(5000);
            }

            // 6. 验证登录结果
            log("\n[7] 验证登录结果");
            log("--------------------------------------------------------------");

            String finalScreen = executeX3270if("Ascii", false);

            if (finalScreen.toUpperCase().contains("WELCOME") ||
                    finalScreen.toUpperCase().contains("OPTION") ||
                    finalScreen.toUpperCase().contains("READY")) {
                log("  ✓ 登录成功!");
                return true;
            } else if (finalScreen.toUpperCase().contains("INVALID")) {
                log("  ✗ 登录失败: 检测到INVALID错误");
                return false;
            } else {
                log("  ⚠ 登录状态不明确,继续执行");
                return true;
            }

        } catch (Exception e) {
            log("");
            log("✗ 登录失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void performTSOOperations() {
        try {
            log("");
            log("==============================================================");
            log("开始TSO操作");
            log("==============================================================");

            // 步骤1: 发送2次Enter
            log("\n[步骤1] 发送2次Enter");
            log("--------------------------------------------------------------");

            log("  第1次Enter:");
            sendEnter(1);

            log("  第2次Enter:");
            sendEnter(1);

            // 步骤2: 输入2并Enter
            log("\n[步骤2] 输入2进入菜单");
            log("--------------------------------------------------------------");

            tabAndInput("2", 1);

            // 步骤3: 输入X并Enter
            log("\n[步骤3] 输入X退出");
            log("--------------------------------------------------------------");

            tabAndInput("X", 1);

            log("");
            log("✓ TSO操作完成!");

        } catch (Exception e) {
            log("");
            log("✗ TSO操作失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean logout() {
        try {
            log("");
            log("==============================================================");
            log("开始登出流程");
            log("==============================================================");

            dumpScreen("登出前的屏幕");

            String currentScreen = executeX3270if("Ascii", false);

            // 从应用菜单退出
            if (currentScreen.toUpperCase().contains("OPTION")) {
                log("\n[1] 从应用菜单退出");
                log("--------------------------------------------------------------");

                executeX3270if("Reset", false);
                Thread.sleep(500);

                executeX3270if("Tab", false);
                Thread.sleep(300);

                getCursorPosition();

                executeX3270if("String(\"X\")", false);
                Thread.sleep(300);

                executeX3270if("Enter", true);  // ⭐ Enter后显示
                Thread.sleep(3000);
            }

            // 发送LOGOFF
            log("\n[2] 发送LOGOFF");
            log("--------------------------------------------------------------");

            executeX3270if("Reset", false);
            Thread.sleep(500);

            executeX3270if("String(\"LOGOFF\")", false);
            Thread.sleep(300);

            executeX3270if("Enter", true);  // ⭐ Enter后显示
            Thread.sleep(5000);

            // 断开连接
            log("\n[3] 断开连接");
            log("--------------------------------------------------------------");

            executeX3270if("Disconnect", false);
            Thread.sleep(1000);

            log("  ✓ 登出完成");
            return true;

        } catch (Exception e) {
            log("");
            log("✗ 登出失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void disconnect() {
        log("");
        log("==============================================================");
        log("关闭ws3270");
        log("==============================================================");

        try {
            if (ws3270Process != null && ws3270Process.isAlive()) {
                log("  尝试正常终止进程...");
                ws3270Process.destroy();

                boolean exited = ws3270Process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);

                if (exited) {
                    log("  ✓ 进程已正常终止");
                } else {
                    log("  ⚠ 进程未正常终止,尝试强制终止...");
                    ws3270Process.destroyForcibly();
                    ws3270Process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                }
            }

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
                    log("总屏幕快照数: " + screenDumpCounter);
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
        System.out.println("TK4- MVS TSO 自动化程序 (可配置版)");
        System.out.println("==============================================================\n");

        // ⭐⭐⭐ 配置参数 ⭐⭐⭐
        boolean debugMode = true;                // 是否开启调试
        boolean onlyShowScreenChanges = true;   // true=只显示变化, false=显示所有

        System.out.println("配置:");
        System.out.println("  调试模式: " + (debugMode ? "开启" : "关闭"));
        System.out.println("  屏幕显示: " + (onlyShowScreenChanges ? "仅显示变化" : "显示所有"));
        System.out.println();

        TN3270Demo demo = new TN3270Demo(debugMode, onlyShowScreenChanges);

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

            // 3. 执行TSO操作
            System.out.println("\n步骤3: 执行TSO操作...\n");
            demo.performTSOOperations();

            System.out.println("\n✓ TSO操作完成!");

            // 4. 等待一下
            System.out.println("\n程序将在10秒后自动登出...");
            for (int i = 10; i > 0; i--) {
                System.out.print("\r剩余 " + i + " 秒... ");
                Thread.sleep(1000);
            }
            System.out.println();

            // 5. 登出
            System.out.println("\n步骤4: 自动登出...\n");
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