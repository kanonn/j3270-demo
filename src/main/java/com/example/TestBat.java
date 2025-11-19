package com.example;

import java.io.*;

public class TestBat {
    public static void main(String[] args) {
        try {
            String batPath = "E:\\01_work\\003-anems-sample\\wc3270-4.4ga6-noinstall-64\\ws3270.bat";

            System.out.println("测试启动 ws3270.bat");
            System.out.println("路径: " + batPath);
            System.out.println("");

            ProcessBuilder pb = new ProcessBuilder(
                    batPath,
                    "-scriptport", "localhost:6001",
                    "-model", "3278-2"
            );

            pb.redirectErrorStream(true);

            System.out.println("启动进程...");
            Process process = pb.start();

            // 启动一个线程读取输出(避免阻塞)
            new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[ws3270] " + line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            // 等待5秒
            System.out.println("等待5秒...");
            Thread.sleep(5000);

            System.out.println("\n进程状态: " + (process.isAlive() ? "运行中" : "已退出"));

            if (!process.isAlive()) {
                System.out.println("退出码: " + process.exitValue());
            }

            // 检查端口
            System.out.println("\n检查端口6001...");
            ProcessBuilder netstatPb = new ProcessBuilder("netstat", "-an");
            Process netstatProc = netstatPb.start();
            BufferedReader netstatReader = new BufferedReader(
                    new InputStreamReader(netstatProc.getInputStream()));

            String netstatLine;
            boolean found = false;
            while ((netstatLine = netstatReader.readLine()) != null) {
                if (netstatLine.contains("6001")) {
                    System.out.println("  " + netstatLine.trim());
                    found = true;
                }
            }

            if (!found) {
                System.out.println("  端口6001未监听!");
            }

            // 检查进程
            System.out.println("\n检查ws3270进程...");
            ProcessBuilder tasklistPb = new ProcessBuilder("tasklist");
            Process tasklistProc = tasklistPb.start();
            BufferedReader tasklistReader = new BufferedReader(
                    new InputStreamReader(tasklistProc.getInputStream()));

            String taskLine;
            boolean processFound = false;
            while ((taskLine = tasklistReader.readLine()) != null) {
                if (taskLine.toLowerCase().contains("ws3270")) {
                    System.out.println("  " + taskLine.trim());
                    processFound = true;
                }
            }

            if (!processFound) {
                System.out.println("  ws3270进程未找到!");
            }

            // 清理
            System.out.println("\n按Enter键终止进程...");
            System.in.read();

            if (process.isAlive()) {
                System.out.println("终止进程...");
                process.destroyForcibly();
            }

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}