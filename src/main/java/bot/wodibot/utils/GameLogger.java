package bot.wodibot.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class GameLogger {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 记录游戏日志
     */
    public static void logGame(long chatId, String action) {
        String timestamp = DATE_FORMAT.format(new Date());
        String logMessage = String.format("[%s] [Chat:%d] %s", timestamp, chatId, action);
        System.out.println("🎮 " + logMessage);
    }

    /**
     * 记录错误日志
     */
    public static void logError(long chatId, String error) {
        String timestamp = DATE_FORMAT.format(new Date());
        String logMessage = String.format("[%s] [Chat:%d] ERROR: %s", timestamp, chatId, error);
        System.err.println("❌ " + logMessage);
    }

    /**
     * 记录玩家加入/退出
     */
    public static void logPlayerAction(long chatId, long userId, String playerName, String action) {
        String timestamp = DATE_FORMAT.format(new Date());
        String logMessage = String.format("[%s] [Chat:%d] [Player:%s(%d)] %s",
                timestamp, chatId, playerName, userId, action);
        System.out.println("👤 " + logMessage);
    }

    /**
     * 记录系统日志
     */
    public static void logSystem(String message) {
        String timestamp = DATE_FORMAT.format(new Date());
        String logMessage = String.format("[%s] [System] %s", timestamp, message);
        System.out.println("⚙️ " + logMessage);
    }

    /**
     * 记录胜率更新
     */
    public static void logStatsUpdate(long userId, String playerName, String action) {
        String timestamp = DATE_FORMAT.format(new Date());
        String logMessage = String.format("[%s] [Player:%s(%d)] %s",
                timestamp, playerName, userId, action);
        System.out.println("📊 " + logMessage);
    }
}