package com.example;

import com.github.filipesimoes.j3270.Emulator;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TN3270Demo {

    private static final String HOST = "localhost";
    private static final int PORT = 3270;
    private static final int SCRIPT_PORT = 6001;
    private static final String TERMINAL_MODEL = "3278-2";

    private static final String WS3270_PATH = "E:\\01_work\\003-anems-sample\\wc3270-4.4ga6-noinstall-64\\ws3270.bat";

    private Emulator emulator;
    private Process ws3270Process;
    private BufferedWriter logWriter;

    public TN3270Demo() {
        try {
            logWriter = new BufferedWriter(new FileWriter("tn3270_fixed.txt", false));
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

    public boolean connect() {
        try {
            // 强制Java使用IPv4
            System.setProperty("java.net.preferIPv4Stack", "true");

            log("==============================================================");
            log("TK4- MVS 连接程序 (手动启动版)");
            log("==============================================================");
            log("配置:");
            log("  主机: " + HOST + ":" + PORT);
            log("  ScriptPort: " + SCRIPT_PORT);
            log("  终端型号: " + TERMINAL_MODEL);
            log("  ws3270路径: " + WS3270_PATH);
            log("");

            // 1. 手动启动ws3270
            log("[1] 手动启动ws3270");
            log("--------------------------------------------------------------");

            ProcessBuilder pb = new ProcessBuilder(
                    WS3270_PATH,
                    "-scriptport", "localhost:" + SCRIPT_PORT,
                    "-model", TERMINAL_MODEL
            );

            pb.redirectErrorStream(true);

            ws3270Process = pb.start();
            log("  ProcessBuilder已执行");

            // 启动线程读取输出
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

            // 等待ws3270启动
            log("  等待3秒...");
            Thread.sleep(3000);

            if (!ws3270Process.isAlive()) {
                log("  ✗ ws3270进程已退出");
                log("  退出码: " + ws3270Process.exitValue());
                return false;
            }
            log("  ✓ ws3270进程运行中");
            log("");

            // 2. 创建Emulator并手动连接TerminalCommander
            log("[2] 创建Emulator实例");
            log("--------------------------------------------------------------");

            emulator = new Emulator(SCRIPT_PORT, java.util.concurrent.Executors.newFixedThreadPool(1));
            emulator.setModel(TERMINAL_MODEL);
            emulator.setVisible(true);
            log("  Emulator实例已创建");
            log("");

            log("[3] 连接TerminalCommander到scriptport");
            log("--------------------------------------------------------------");

            // 使用反射手动连接TerminalCommander
            java.lang.reflect.Field commanderField = Emulator.class.getDeclaredField("commander");
            commanderField.setAccessible(true);
            Object commander = commanderField.get(emulator);

            java.lang.reflect.Method connectMethod = commander.getClass().getDeclaredMethod("connect");
            connectMethod.invoke(commander);

            log("  ✓ TerminalCommander已连接到scriptport");
            log("");

            // 3. 连接到TK4-
            log("[4] 连接到TK4-");
            log("--------------------------------------------------------------");

            boolean connected = emulator.connect(HOST + ":" + PORT);
            if (!connected) {
                log("  ✗ 连接失败");
                return false;
            }
            log("  ✓ 已连接");
            log("");

            // 4. 等待屏幕
            log("[5] 等待TSO登录屏幕");
            log("--------------------------------------------------------------");

            emulator.waitField(10);
            Thread.sleep(2000);

            // 5. 读取屏幕
            log("\n[6] TSO登录屏幕内容");
            log("--------------------------------------------------------------");

            for (int row = 1; row <= 24; row++) {
                String line = emulator.getText(row, 1, 80);
                if (line != null && !line.trim().isEmpty()) {
                    log(String.format("  Row %2d: %s", row, line));
                }
            }

            log("");
            log("==============================================================");
            log("✓ 连接成功!");
            log("==============================================================");

            return true;

        } catch (Exception e) {
            log("");
            log("✗ 错误: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean login(String username, String password, int userRow, int userCol, int passRow, int passCol) {
        try {
            log("");
            log("==============================================================");
            log("登录TSO");
            log("==============================================================");

            emulator.waitField(3);

            log("输入USERID: " + username);
            log("  位置: 行=" + userRow + ", 列=" + userCol);
            emulator.fillField(userRow, userCol, username);
            Thread.sleep(500);

            log("输入PASSWORD: ******");
            log("  位置: 行=" + passRow + ", 列=" + passCol);
            emulator.fillField(passRow, passCol, password);
            Thread.sleep(500);

            log("提交登录...");
            emulator.sendEnter();

            log("等待TSO响应...");
            emulator.waitField(15);
            Thread.sleep(3000);

            log("");
            log("登录后屏幕:");
            log("--------------------------------------------------------------");

            for (int row = 1; row <= 24; row++) {
                String line = emulator.getText(row, 1, 80);
                if (line != null && !line.trim().isEmpty()) {
                    log(String.format("  Row %2d: %s", row, line));
                }
            }

            log("");
            log("✓ 登录完成");

            return true;

        } catch (Exception e) {
            log("");
            log("✗ 登录失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void disconnect() {
        log("");
        log("关闭连接...");

        try {
            if (emulator != null) {
                emulator.close();
                log("  ✓ Emulator已关闭");
            }

            if (ws3270Process != null && ws3270Process.isAlive()) {
                ws3270Process.destroyForcibly();
                ws3270Process.waitFor();
                log("  ✓ ws3270进程已终止");
            }
        } catch (Exception e) {
            log("  关闭时出错: " + e.getMessage());
        } finally {
            if (logWriter != null) {
                try {
                    logWriter.close();
                } catch (IOException e) {
                    // 忽略
                }
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("\n==============================================================");
        System.out.println("TK4- MVS 连接程序 - 修复版");
        System.out.println("==============================================================\n");

        TN3270Demo demo = new TN3270Demo();

        try {
            // 连接
            if (!demo.connect()) {
                System.err.println("\n❌ 连接失败");
                System.err.println("请查看日志: tn3270_fixed.txt");
                return;
            }

            System.out.println("\n✓ 连接成功!");
            System.out.println("ws3270窗口应该显示TSO登录界面");
            System.out.println("");

            // 获取字段位置
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

            System.out.println("请查看ws3270窗口,找到 'USERID' 字段");
            System.out.print("  USERID所在行号: ");
            int userRow = Integer.parseInt(consoleReader.readLine().trim());
            System.out.print("  USERID输入列号: ");
            int userCol = Integer.parseInt(consoleReader.readLine().trim());

            System.out.println("");
            System.out.println("请查看ws3270窗口,找到 'PASSWORD' 字段");
            System.out.print("  PASSWORD所在行号: ");
            int passRow = Integer.parseInt(consoleReader.readLine().trim());
            System.out.print("  PASSWORD输入列号: ");
            int passCol = Integer.parseInt(consoleReader.readLine().trim());

            // 登录
            System.out.println("\n开始登录...");
            demo.login("HERC01", "CUL8TR", userRow, userCol, passRow, passCol);

            // 等待
            System.out.println("\n程序将在30秒后退出...");
            for (int i = 30; i > 0; i--) {
                System.out.print("\r剩余 " + i + " 秒... ");
                Thread.sleep(1000);
            }
            System.out.println();

        } catch (Exception e) {
            System.err.println("\n❌ 错误: " + e.getMessage());
            e.printStackTrace();
        } finally {
            demo.disconnect();
        }

        System.out.println("\n==============================================================");
        System.out.println("程序结束");
        System.out.println("详细日志: tn3270_fixed.txt");
        System.out.println("==============================================================\n");
    }
}