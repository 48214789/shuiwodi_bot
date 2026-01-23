package bot.wodibot.word;

import bot.wodibot.BotConfig;
import bot.wodibot.utils.GameLogger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WordReloadTask {

    private static ScheduledExecutorService scheduler;

    public static void start() {
        System.out.println("🔄 启动词库自动重载任务...");

        scheduler = Executors.newSingleThreadScheduledExecutor();

        // 第一次立即加载
        try {
            int count = WordService.reloadAndGetCount();
            System.out.println("✅ 初始加载词库: " + count + " 个词对");
            GameLogger.logGame(-1, "初始加载词库: " + count + " 个词对");
        } catch (Exception e) {
            System.err.println("❌ 初始加载词库失败: " + e.getMessage());
            GameLogger.logError(-1, "初始加载词库失败: " + e.getMessage());
        }

        // 使用配置的重载间隔（分钟）
        int reloadInterval = BotConfig.getWordReloadInterval();
        System.out.println("⏰ 词库重载间隔: " + reloadInterval + " 分钟");

        scheduler.scheduleAtFixedRate(() -> {
            try {
                int before = WordService.getWordCount();
                WordService.reload();
                int after = WordService.getWordCount();
                String message = "🔄 词库重载完成: " + before + " → " + after + " 个词对";
                System.out.println(message);
                GameLogger.logGame(-1, message);
            } catch (Exception e) {
                String error = "❌ 词库重载失败: " + e.getMessage();
                System.err.println(error);
                GameLogger.logError(-1, error);
            }
        }, reloadInterval, reloadInterval, TimeUnit.MINUTES);

        // 在 start() 方法中添加胜率清理
        scheduler.scheduleAtFixedRate(() -> {
            try {
                bot.wodibot.stats.StatsService.cleanupOldStats();
                System.out.println("🧹 胜率数据清理完成");
            } catch (Exception e) {
                System.err.println("❌ 胜率数据清理失败: " + e.getMessage());
            }
        }, 7, 7, TimeUnit.DAYS); // 每7天清理一次
    }

    public static void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
            System.out.println("🛑 词库重载任务已停止");
        }
    }
}