package bot.wodibot.utils;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class GameLogger {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat FILE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final String LOG_DIR = "logs";
    
    static {
        // 创建日志目录
        java.io.File dir = new java.io.File(LOG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    
    /**
     * 记录游戏日志
     */
    public static void logGame(long chatId, String action) {
        String timestamp = DATE_FORMAT.format(new Date());
        String logMessage = String.format("[%s] [Chat:%d] %s", timestamp, chatId, action);
        
        // 控制台输出
        System.out.println("🎮 " + logMessage);
        
        // 文件日志
        try {
            String fileName = LOG_DIR + "/game_" + FILE_FORMAT.format(new Date()) + ".log";
            try (PrintWriter out = new PrintWriter(
                    new FileWriter(fileName, true))) {
                out.println(logMessage);
            }
        } catch (Exception e) {
            System.err.println("❌ 写入日志文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 记录错误日志
     */
    public static void logError(long chatId, String error) {
        String timestamp = DATE_FORMAT.format(new Date());
        String logMessage = String.format("[%s] [Chat:%d] ERROR: %s", timestamp, chatId, error);
        
        // 控制台输出
        System.err.println("❌ " + logMessage);
        
        // 错误日志文件
        try {
            String fileName = LOG_DIR + "/error_" + FILE_FORMAT.format(new Date()) + ".log";
            try (PrintWriter out = new PrintWriter(
                    new FileWriter(fileName, true))) {
                out.println(logMessage);
            }
        } catch (Exception e) {
            System.err.println("❌ 写入错误日志文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 记录玩家加入/退出
     */
    public static void logPlayerAction(long chatId, long userId, String playerName, String action) {
        String timestamp = DATE_FORMAT.format(new Date());
        String logMessage = String.format("[%s] [Chat:%d] [Player:%s(%d)] %s", 
            timestamp, chatId, playerName, userId, action);
        
        System.out.println("👤 " + logMessage);
        
        try {
            String fileName = LOG_DIR + "/players_" + FILE_FORMAT.format(new Date()) + ".log";
            try (PrintWriter out = new PrintWriter(
                    new FileWriter(fileName, true))) {
                out.println(logMessage);
            }
        } catch (Exception e) {
            System.err.println("❌ 写入玩家日志失败: " + e.getMessage());
        }
    }
}