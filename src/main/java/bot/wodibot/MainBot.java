package bot.wodibot;

import bot.wodibot.game.GameRoom;
import bot.wodibot.utils.BotUtils;
import bot.wodibot.utils.GameLogger;
import bot.wodibot.word.WordReloadTask;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;

public class MainBot extends TelegramLongPollingBot {
    
    private static boolean STARTED = false;
    private final Map<Long, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastActivityTime = new ConcurrentHashMap<>();
    
    // 定时清理线程
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
    
    public MainBot() {
        super(BotConfig.getBotToken());
        STARTED = true;
        startCleanupTask();
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        try {
            System.out.println("收到更新: " + update.getUpdateId());
            
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
            String text = update.getMessage().getText();
            
            // 记录活动时间
            lastActivityTime.put(chatId, System.currentTimeMillis());
            
            // 管理员命令处理
            if (BotConfig.isAdmin(String.valueOf(userId))) {
                handleAdminCommands(chatId, userId, text);
                return;
            }
            
            // 普通用户命令
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
                stats.append("• 聊天ID: ").append(id);
                // 这里可以添加更多房间信息
                stats.append("\n");
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
                System.exit(0);
                break;
                
            case "/status":
                String status = "🤖 *机器人状态*\n" +
                               "✅ 运行中\n" +
                               "👥 活跃房间: " + rooms.size() + "\n" +
                               "👑 管理员: " + String.join(", ", BotConfig.getAdminIds()) + "\n" +
                               "📊 词库数量: " + bot.wodibot.word.WordService.getWordCount() + "\n" +
                               "⏰ 运行时间: " + getUptime() + "\n" +
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
        }
    }
    
    private String getUptime() {
        // 这里可以记录启动时间来计算运行时长
        return "未知";
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
               "/stats - 查看机器人统计\n\n" +
               "👑 管理员命令：\n" +
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
    
    // 重启机器人的方法
    private void restartBot() {
        try {
            // 重置启动标志
            resetStartFlag();
            
            // 关闭清理任务
            cleanupScheduler.shutdown();
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
            
            // 等待1秒
            Thread.sleep(1000);
            
            // 重新启动
            GameLogger.logGame(-1, "重新启动机器人...");
            
            // 创建新实例
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            MainBot newBot = new MainBot();
            botsApi.registerBot(newBot);
            
            GameLogger.logGame(-1, "✅ 机器人重启成功");
            
        } catch (Exception e) {
            GameLogger.logError(-1, "❌ 重启失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // 重置启动标志的方法（通过反射）
    private void resetStartFlag() {
        try {
            java.lang.reflect.Field field = MainBot.class.getDeclaredField("STARTED");
            field.setAccessible(true);
            field.set(null, false);
            System.out.println("🔄 重置启动标志");
        } catch (Exception e) {
            System.err.println("重置启动标志失败: " + e.getMessage());
        }
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
    
    @Override
    public String getBotUsername() {
        return BotConfig.getBotUsername();
    }
    
    @Override
    public String getBotToken() {
        return BotConfig.getBotToken();
    }
    
    public static void main(String[] args) {
        try {
            System.out.println("🚀 启动谁是卧底游戏机器人...");
            System.out.println("🤖 Bot Token: " + BotConfig.getBotToken());
            System.out.println("👑 管理员ID: " + String.join(", ", BotConfig.getAdminIds()));
            
            // 检查配置
            if (!BotConfig.isValid()) {
                System.err.println("⚠️ 配置无效，请检查配置文件");
            }
            
            // 启动词库自动重载
            WordReloadTask.start();
            
            // 创建并注册机器人
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            MainBot bot = new MainBot();
            botsApi.registerBot(bot);
            
            System.out.println("✅ 机器人启动成功!");
            System.out.println("🤖 Bot用户名: " + bot.getBotUsername());
            System.out.println("📡 等待消息中...");
            
            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("🤖 机器人正在关闭...");
                STARTED = false;
                bot.cleanupScheduler.shutdown();
                try {
                    if (!bot.cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        bot.cleanupScheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    bot.cleanupScheduler.shutdownNow();
                }
                GameLogger.logGame(-1, "机器人关闭");
            }));
            
        } catch (Exception e) {
            GameLogger.logError(-1, "❌ 机器人启动失败: " + e.getMessage());
            e.printStackTrace();
            // 确保在异常时重置标志
            STARTED = false;
            System.exit(1);
        }
    }
}