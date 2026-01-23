package bot.wodibot;

import bot.wodibot.game.GameRoom;
import bot.wodibot.utils.BotUtils;
import bot.wodibot.utils.GameLogger;
import bot.wodibot.word.WordReloadTask;
import bot.wodibot.model.PlayerStats;
import bot.wodibot.stats.StatsService;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

public class MainBot extends TelegramLongPollingBot {

    private static boolean STARTED = false;
    private static FileLock lockFileLock = null;
    private static RandomAccessFile lockFile = null;
    private static FileChannel lockChannel = null;
    
    private final Map<Long, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastActivityTime = new ConcurrentHashMap<>();

    // 定时清理线程
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

    public MainBot() {
        // 直接使用配置中的token
        super(BotConfig.getBotToken());
        STARTED = true;

        System.out.println("🤖 Bot实例创建成功");
        System.out.println("   Token长度: " + (BotConfig.getBotToken() != null ? BotConfig.getBotToken().length() : 0));
        System.out.println("   用户名: " + getBotUsername());

        startCleanupTask();
    }

    @Override
    public String getBotUsername() {
        // 直接从配置获取
        return BotConfig.getBotUsername();
    }

    @Override
    public String getBotToken() {
        // 直接从配置获取
        return BotConfig.getBotToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            GameLogger.logSystem("收到更新: " + update.getUpdateId());

            // 处理按钮回调
            if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
                return;
            }

            // 处理普通消息
            if (!update.hasMessage() || !update.getMessage().hasText()) {
                return;
            }

            long chatId = update.getMessage().getChatId();
            long userId = update.getMessage().getFrom().getId();
            String text = update.getMessage().getText().trim();

            // 记录活动时间
            lastActivityTime.put(chatId, System.currentTimeMillis());

            GameLogger.logPlayerAction(chatId, userId,
                    update.getMessage().getFrom().getFirstName(),
                    "消息: " + (text.length() > 20 ? text.substring(0, 20) + "..." : text));

            // 管理员命令处理
            if (BotConfig.isAdmin(String.valueOf(userId))) {
                handleAdminCommands(chatId, userId, text);
            }

            // 普通用户命令
            if ("/p".equals(text)) {
                showPlayerStats(chatId, userId, update.getMessage().getFrom().getFirstName());
                return;
            }

            if ("/ph".equals(text)) {
                showLeaderboard(chatId);
                return;
            }
            if ("/help".equals(text)) {
                BotUtils.sendMessage(this, chatId, getHelpMessage());
                return;
            }

            if ("/stats".equals(text)) {
                showBotStats(chatId);
                return;
            }

            // 路由到游戏房间
            rooms.computeIfAbsent(chatId, GameRoom::new)
                    .onMessage(this, update.getMessage());

        } catch (Exception e) {
            GameLogger.logError(-1, "处理更新时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 显示玩家个人胜率
     */
    private void showPlayerStats(long chatId, long userId, String userName) {
        PlayerStats stats = StatsService.getPlayerStats(userId);

        String message;
        if (stats == null || stats.getTotalGames() == 0) {
            message = String.format("📊 *%s 的战绩*\n\n" +
                    "您还没有进行过游戏，快去开始一局吧！\n" +
                    "使用 /startgame 开始新游戏", userName);
        } else {
            message = stats.getFormattedStats();
        }

        BotUtils.sendMessage(this, chatId, message);
    }

    /**
     * 显示胜率排行榜
     */
    private void showLeaderboard(long chatId) {
        List<PlayerStats> leaderboard = StatsService.getLeaderboard(10); // 取前10名

        if (leaderboard.isEmpty()) {
            BotUtils.sendMessage(this, chatId, "📊 *胜率排行榜*\n\n暂无数据，快来开始第一局游戏吧！");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🏆 *胜率排行榜*\n\n");

        for (int i = 0; i < Math.min(leaderboard.size(), 10); i++) {
            PlayerStats stats = leaderboard.get(i);

            sb.append(getRankEmoji(i + 1)).append(" ").append(stats.playerName).append("\n");
            sb.append("   战绩: ").append(stats.getWins()).append("胜")
                    .append(stats.getLosses()).append("负 (胜率: ")
                    .append(String.format("%.1f%%", stats.getWinRate())).append(")\n");
            sb.append("   总游戏: ").append(stats.getTotalGames()).append("场\n");

            if (i < leaderboard.size() - 1) {
                sb.append("   \n");
            }
        }

        // 添加统计信息
        sb.append("\n📈 统计信息:\n");
        sb.append("总记录玩家: ").append(bot.wodibot.stats.StatsService.getStatsCount()).append(" 人\n");
        sb.append("更新日期: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date()));

        BotUtils.sendMessage(this, chatId, sb.toString());
    }

    /**
     * 获取排名对应的emoji
     */
    private String getRankEmoji(int rank) {
        switch (rank) {
            case 1:
                return "🥇";
            case 2:
                return "🥈";
            case 3:
                return "🥉";
            default:
                return "🏅";
        }
    }

    private void startCleanupTask() {
        // 每30分钟清理一次不活跃的房间
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupInactiveRooms();
            } catch (Exception e) {
                GameLogger.logError(-1, "清理任务失败: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.MINUTES);
    }

    private void cleanupInactiveRooms() {
        long now = System.currentTimeMillis();
        long inactiveTimeout = 2 * 60 * 60 * 1000; // 2小时

        int cleaned = 0;
        Iterator<Map.Entry<Long, GameRoom>> iterator = rooms.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, GameRoom> entry = iterator.next();
            Long lastActive = lastActivityTime.get(entry.getKey());

            if (lastActive == null || (now - lastActive) > inactiveTimeout) {
                GameLogger.logGame(entry.getKey(), "清理不活跃房间");
                iterator.remove();
                lastActivityTime.remove(entry.getKey());
                cleaned++;
            }
        }

        if (cleaned > 0) {
            System.out.println("🧹 清理了 " + cleaned + " 个不活跃房间");
        }
    }

    private void showBotStats(long chatId) {
        StringBuilder stats = new StringBuilder("📈 *机器人统计*\n\n");
        stats.append("运行状态: ").append(STARTED ? "✅ 正常" : "❌ 停止").append("\n");
        stats.append("活跃房间: ").append(rooms.size()).append("\n");
        stats.append("配置状态: ").append(BotConfig.isValid() ? "✅ 有效" : "❌ 无效").append("\n");
        stats.append("词库数量: ").append(bot.wodibot.word.WordService.getWordCount()).append("\n\n");

        // 活跃房间详情
        if (!rooms.isEmpty()) {
            stats.append("🏠 *活跃房间*:\n");
            rooms.forEach((id, room) -> {
                stats.append("• 聊天ID: ").append(id).append("\n");
            });
        } else {
            stats.append("暂无活跃房间");
        }

        BotUtils.sendMessage(this, chatId, stats.toString());
    }

    private void handleAdminCommands(long chatId, long userId, String text) {
        switch (text) {
            case "/restart":
                BotUtils.sendMessage(this, chatId, "🔄 机器人重启中...");
                GameLogger.logGame(chatId, "管理员重启机器人");
                restartBot();
                break;

            case "/stop":
                BotUtils.sendMessage(this, chatId, "🛑 机器人停止中...");
                GameLogger.logGame(chatId, "管理员停止机器人");
                safeShutdown();
                break;

            case "/status":
                String status = "🤖 *机器人状态*\n" +
                        "✅ 运行中\n" +
                        "👥 活跃房间: " + rooms.size() + "\n" +
                        "👑 管理员: " + String.join(", ", BotConfig.getAdminIds()) + "\n" +
                        "📊 词库数量: " + bot.wodibot.word.WordService.getWordCount() + "\n" +
                        BotConfig.getConfigStatus();
                BotUtils.sendMessage(this, chatId, status);
                break;

            case "/clean":
                int count = rooms.size();
                rooms.clear();
                lastActivityTime.clear();
                BotUtils.sendMessage(this, chatId, "🧹 已清理所有房间 (" + count + "个)");
                GameLogger.logGame(chatId, "管理员清理了所有房间");
                break;

            case "/reload":
                int wordCount = bot.wodibot.word.WordService.reloadAndGetCount();
                BotUtils.sendMessage(this, chatId, "🔄 词库已重新加载: " + wordCount + " 个词对");
                GameLogger.logGame(chatId, "管理员重载词库");
                break;

            case "/config":
                BotUtils.sendMessage(this, chatId, BotConfig.getConfigStatus());
                break;

            case "/rooms":
                StringBuilder roomsInfo = new StringBuilder("🏠 *当前房间*\n\n");
                roomsInfo.append("总计: ").append(rooms.size()).append(" 个\n\n");
                rooms.forEach((id, room) -> {
                    roomsInfo.append("聊天ID: ").append(id).append("\n");
                });
                BotUtils.sendMessage(this, chatId, roomsInfo.toString());
                break;
            case "/stats_reload":
                // 重新加载胜率数据
                StatsService.loadStats();
                BotUtils.sendMessage(this, chatId, "🔄 胜率数据已重新加载");
                GameLogger.logGame(chatId, "管理员重载胜率数据");
                break;

            case "/stats_clear":
                // 清理胜率数据（危险操作）
                BotUtils.sendMessage(this, chatId, "⚠️ 此操作将清空所有胜率数据，确认请输入 /stats_clear_confirm");
                break;

            case "/stats_clear_confirm":
                // 确认清理胜率数据
                try {
                    StatsService.resetAllStats();
                    BotUtils.sendMessage(this, chatId, "🧹 胜率数据已清空");
                    GameLogger.logGame(chatId, "管理员清空了胜率数据");
                } catch (Exception e) {
                    BotUtils.sendMessage(this, chatId, "❌ 清空胜率数据失败: " + e.getMessage());
                }
                break;
        }
    }

    private String getHelpMessage() {
        return "🤖 *谁是卧底游戏机器人*\n\n" +
                "📋 用户命令：\n" +
                "/startgame - 开始新游戏\n" +
                "/players - 查看玩家列表\n" +
                "/status - 查看游戏状态\n" +
                "/rules - 查看游戏规则\n" +
                "/help - 显示此帮助信息\n" +
                "/cancel - 取消当前游戏\n" +
                "/stats - 查看机器人统计\n" +
                "/p - 查看我的胜率\n" +
                "/ph - 查看胜率排行榜\n" +
                "\n👑 管理员命令：\n" +
                "/status - 查看机器人状态\n" +
                "/restart - 重启机器人\n" +
                "/stop - 停止机器人\n" +
                "/clean - 清理所有房间\n" +
                "/reload - 重载词库\n" +
                "/config - 查看配置状态\n" +
                "/rooms - 查看当前房间\n\n" +
                "⚙️ 当前配置：\n" +
                "最小玩家: " + BotConfig.getMinPlayers() + "\n" +
                "最大玩家: " + BotConfig.getMaxPlayers() + "\n" +
                "发言时间: " + BotConfig.getSpeakingTime() + "秒";
    }

    // 处理按钮回调
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        try {
            String callbackData = callbackQuery.getData();
            long userId = callbackQuery.getFrom().getId();
            long chatId = callbackQuery.getMessage().getChatId();
            int messageId = callbackQuery.getMessage().getMessageId();
            String queryId = callbackQuery.getId();

            GameLogger.logPlayerAction(chatId, userId, callbackQuery.getFrom().getFirstName(),
                    "按钮回调: " + callbackData);

            // 立即应答回调查询（避免按钮转圈）
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(queryId);
            execute(answer);

            // 记录活动时间
            lastActivityTime.put(chatId, System.currentTimeMillis());

            // 根据回调数据执行不同操作
            GameRoom room = rooms.get(chatId);
            if (room == null) {
                return;
            }

            if ("join_game".equals(callbackData)) {
                room.onCallbackJoin(this, userId, callbackQuery.getFrom().getFirstName(),
                        messageId, callbackQuery);
            } else if ("start_now".equals(callbackData)) {
                room.onCallbackStart(this, userId, messageId);
            }

        } catch (Exception e) {
            GameLogger.logError(-1, "处理回调查询时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 尝试获取文件锁来确保单实例运行
     */
    private static boolean acquireLock() {
        File lockFileObj = new File("bot.lock");
        try {
            // 尝试获取文件锁
            lockFile = new RandomAccessFile(lockFileObj, "rw");
            lockChannel = lockFile.getChannel();
            lockFileLock = lockChannel.tryLock();
            
            if (lockFileLock != null) {
                System.out.println("🔒 成功获取文件锁，确保单实例运行");
                // 写入进程信息到锁文件
                lockFile.writeBytes("PID: " + ProcessHandle.current().pid() + "\n");
                lockFile.writeBytes("Start Time: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(new java.util.Date()) + "\n");
                return true;
            } else {
                System.err.println("❌ 无法获取文件锁，可能已有另一个Bot实例在运行");
                return false;
            }
        } catch (IOException e) {
            System.err.println("❌ 创建锁文件失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 释放文件锁
     */
    private static void releaseLock() {
        try {
            if (lockFileLock != null && lockFileLock.isValid()) {
                lockFileLock.release();
                System.out.println("🔓 已释放文件锁");
            }
            if (lockChannel != null) {
                lockChannel.close();
            }
            if (lockFile != null) {
                lockFile.close();
            }
            
            // 删除锁文件
            File lockFileObj = new File("bot.lock");
            if (lockFileObj.exists()) {
                lockFileObj.delete();
            }
        } catch (IOException e) {
            System.err.println("❌ 释放文件锁失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查是否已有实例运行（通过锁文件）
     */
    private static boolean isAlreadyRunning() {
        File lockFileObj = new File("bot.lock");
        if (lockFileObj.exists()) {
            try {
                // 尝试读取锁文件内容
                RandomAccessFile raf = new RandomAccessFile(lockFileObj, "r");
                String content = raf.readLine();
                raf.close();
                
                if (content != null && content.startsWith("PID: ")) {
                    String pidStr = content.substring(5).trim();
                    try {
                        long pid = Long.parseLong(pidStr);
                        // 检查进程是否仍在运行
                        ProcessHandle process = ProcessHandle.of(pid).orElse(null);
                        if (process != null && process.isAlive()) {
                            System.err.println("⚠️ 检测到Bot实例仍在运行 (PID: " + pid + ")");
                            return true;
                        } else {
                            System.out.println("🧹 发现已停止进程的锁文件，将清理");
                            lockFileObj.delete();
                            return false;
                        }
                    } catch (NumberFormatException e) {
                        // PID格式错误，删除旧锁文件
                        lockFileObj.delete();
                        return false;
                    }
                }
            } catch (IOException e) {
                // 无法读取锁文件，删除它
                lockFileObj.delete();
                return false;
            }
        }
        return false;
    }
    
    /**
     * 安全关闭机器人
     */
    private void safeShutdown() {
        try {
            System.out.println("\n🤖 机器人正在安全关闭...");
            
            // 1. 停止所有游戏房间
            rooms.clear();
            lastActivityTime.clear();
            
            // 2. 停止清理任务
            if (cleanupScheduler != null) {
                cleanupScheduler.shutdown();
                try {
                    if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        cleanupScheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    cleanupScheduler.shutdownNow();
                }
            }
            
            // 3. 停止词库重载任务
            WordReloadTask.stop();
            
            // 4. 保存胜率数据
            try {
                StatsService.forceSaveStats();
            } catch (Exception e) {
                System.err.println("❌ 保存胜率数据失败: " + e.getMessage());
            }
            
            // 5. 释放文件锁
            releaseLock();
            
            // 6. 设置状态为停止
            STARTED = false;
            
            System.out.println("✅ 机器人已安全关闭");
            GameLogger.logSystem("机器人关闭");
            
            // 7. 退出程序
            System.exit(0);
            
        } catch (Exception e) {
            System.err.println("❌ 关闭机器人时出错: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("=".repeat(50));
            
            // 检查是否已有实例在运行
            if (isAlreadyRunning()) {
                System.err.println("❌ 检测到Bot已在运行中，请不要重复启动！");
                System.err.println("💡 如果确定没有运行，请删除 bot.lock 文件后重试");
                System.exit(1);
            }
            
            // 尝试获取文件锁
            if (!acquireLock()) {
                System.err.println("❌ 无法启动：可能已有另一个Bot实例在运行");
                System.exit(1);
            }

            // 显示当前工作目录
            File currentDir = new File(".");
            System.out.println("📁 当前工作目录: " + currentDir.getAbsolutePath());
            System.out.println("📂 目录内容:");
            File[] files = currentDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    System.out.println("  " + (file.isDirectory() ? "📁 " : "📄 ") + file.getName());
                }
            }

            // 检查配置文件
            System.out.println("\n🔧 检查配置文件...");
            File configFile = new File("src/main/resources/bot.properties");
            System.out.println("  配置文件路径: " + configFile.getAbsolutePath());
            System.out.println("  配置文件存在: " + configFile.exists());

            // 检查词库文件
            File wordFile = new File("src/main/resources/words.txt");
            System.out.println("  词库文件路径: " + wordFile.getAbsolutePath());
            System.out.println("  词库文件存在: " + wordFile.exists());

            // 加载配置
            System.out.println("\n⚙️ 加载配置...");
            BotConfig.reload();

            if (!BotConfig.isValid()) {
                System.err.println("❌ 配置无效，请检查配置文件");
                System.out.println("尝试使用默认配置启动...");
            }

            System.out.println("🤖 Bot Token: " + (BotConfig.getBotToken() != null ? "***"
                    + BotConfig.getBotToken().substring(0, Math.min(10, BotConfig.getBotToken().length())) + "..."
                    : "未设置"));
            System.out.println("🤖 Bot用户名: " + BotConfig.getBotUsername());
            System.out.println("👑 管理员ID: " + String.join(", ", BotConfig.getAdminIds()));

            // 检查词库
            System.out.println("\n📚 检查词库...");
            int wordCount = bot.wodibot.word.WordService.getWordCount();
            System.out.println("✅ 词库数量: " + wordCount + " 个词对");

            if (wordCount == 0) {
                System.err.println("⚠️ 词库为空，将使用默认词库");
            }

            // 启动词库自动重载
            System.out.println("\n🔄 启动词库自动重载...");
            WordReloadTask.start();

            // 添加随机延迟，避免连接冲突
            Random random = new Random();
            int delay = random.nextInt(2000) + 1000; // 1-3秒随机延迟
            System.out.println("⏰ 随机延迟 " + delay + "ms 后启动...");
            Thread.sleep(delay);

            // 创建并注册机器人
            System.out.println("\n🤖 创建并注册机器人...");
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            MainBot bot = new MainBot();
            botsApi.registerBot(bot);

            System.out.println("=".repeat(50));
            System.out.println("✅ 机器人启动成功!");
            System.out.println("🤖 Bot用户名: " + bot.getBotUsername());
            System.out.println("📡 等待消息中...");
            System.out.println("💡 发送 /startgame 开始游戏");
            System.out.println("=".repeat(50));

            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    bot.safeShutdown();
                } catch (Exception e) {
                    System.err.println("❌ 关闭钩子执行失败: " + e.getMessage());
                }
            }));

            // 保持主线程运行
            while (STARTED) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("\n❌ 机器人启动失败!");
            System.err.println("错误信息: " + e.getMessage());
            e.printStackTrace();

            // 释放文件锁
            releaseLock();

            // 提供调试建议
            System.err.println("\n🔧 调试建议:");
            System.err.println("1. 检查是否已有Bot实例在运行");
            System.err.println("2. 等待30秒后重试");
            System.err.println("3. 删除 bot.lock 文件");
            System.err.println("4. 检查 bot.properties 文件");
            System.err.println("5. 检查网络连接");

            STARTED = false;
            System.exit(1);
        }
    }

    // 重启机器人的方法
    private void restartBot() {
        try {
            System.out.println("🔄 重启机器人...");
            GameLogger.logSystem("管理员请求重启机器人");
            
            // 1. 先发送重启通知
            BotUtils.sendMessage(this, -1, "🔄 机器人正在重启，请稍候30秒...");
            
            // 2. 停止清理任务
            if (cleanupScheduler != null) {
                cleanupScheduler.shutdown();
                try {
                    if (!cleanupScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                        cleanupScheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    cleanupScheduler.shutdownNow();
                }
            }
            
            // 3. 保存胜率数据
            try {
                StatsService.forceSaveStats();
            } catch (Exception e) {
                System.err.println("❌ 保存胜率数据失败: " + e.getMessage());
            }
            
            // 4. 释放文件锁（重要！）
            releaseLock();
            
            // 5. 重置启动标志
            STARTED = false;
            
            // 6. 等待5秒确保资源释放
            Thread.sleep(5000);
            
            // 7. 创建新实例（在新的线程中）
            new Thread(() -> {
                try {
                    System.out.println("🚀 启动新Bot实例...");
                    
                    // 等待额外的5秒确保旧实例完全停止
                    Thread.sleep(5000);
                    
                    // 重新启动
                    main(new String[]{});
                    
                } catch (Exception e) {
                    System.err.println("❌ 重启失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
            
            // 8. 当前线程自然结束
            
        } catch (Exception e) {
            GameLogger.logError(-1, "❌ 重启失败: " + e.getMessage());
            e.printStackTrace();
            BotUtils.sendMessage(this, -1, "❌ 重启失败: " + e.getMessage());
        }
    }
}