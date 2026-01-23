package bot.wodibot.utils;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.util.List;

public class BotUtils {
    
    /**
     * 发送消息到指定聊天
     */
    public static void sendMessage(AbsSender bot, long chatId, String text) {
        sendMessage(bot, chatId, text, "Markdown");
    }
    
    /**
     * 发送消息到指定聊天，指定解析模式
     */
    public static void sendMessage(AbsSender bot, long chatId, String text, String parseMode) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(text);
            if (parseMode != null) {
                message.setParseMode(parseMode);
            }
            bot.execute(message);
            
            // 日志（截断长消息）
            String logText = text.length() > 50 ? text.substring(0, 50) + "..." : text;
            System.out.println("📤 发送消息到 " + chatId + ": " + logText);
            
        } catch (TelegramApiException e) {
            System.err.println("❌ 发送消息失败: " + e.getMessage());
            if (e.getMessage() != null) {
                if (e.getMessage().contains("Forbidden")) {
                    System.err.println("⚠️ 用户可能屏蔽了机器人");
                } else if (e.getMessage().contains("chat not found")) {
                    System.err.println("⚠️ 聊天不存在");
                }
            }
        } catch (Exception e) {
            System.err.println("❌ 发送消息时发生未知错误: " + e.getMessage());
        }
    }
    
    /**
     * 发送消息并返回消息ID
     */
    public static Integer sendMessageWithId(AbsSender bot, long chatId, String text) {
        return sendMessageWithId(bot, chatId, text, "Markdown");
    }
    
    /**
     * 发送消息并返回消息ID
     */
    public static Integer sendMessageWithId(AbsSender bot, long chatId, String text, String parseMode) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(text);
            if (parseMode != null) {
                message.setParseMode(parseMode);
            }
            org.telegram.telegrambots.meta.api.objects.Message sentMessage = bot.execute(message);
            
            // 日志（截断长消息）
            String logText = text.length() > 50 ? text.substring(0, 50) + "..." : text;
            System.out.println("📤 发送消息到 " + chatId + ": " + logText + " (ID: " + sentMessage.getMessageId() + ")");
            
            return sentMessage.getMessageId();
        } catch (TelegramApiException e) {
            System.err.println("❌ 发送消息失败: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("❌ 发送消息时发生未知错误: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 删除消息
     */
    public static boolean deleteMessage(AbsSender bot, long chatId, int messageId) {
        try {
            DeleteMessage deleteMessage = new DeleteMessage();
            deleteMessage.setChatId(String.valueOf(chatId));
            deleteMessage.setMessageId(messageId);
            
            bot.execute(deleteMessage);
            System.out.println("🗑️ 删除消息 " + chatId + ":" + messageId);
            return true;
        } catch (TelegramApiException e) {
            // 如果消息已经被删除或其他错误，忽略
            System.err.println("⚠️ 删除消息失败: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("❌ 删除消息时发生未知错误: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 批量删除消息
     */
    public static void deleteMessages(AbsSender bot, long chatId, List<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }
        
        System.out.println("🗑️ 开始批量删除 " + messageIds.size() + " 条消息");
        int deletedCount = 0;
        
        for (Integer messageId : messageIds) {
            if (deleteMessage(bot, chatId, messageId)) {
                deletedCount++;
            }
        }
        
        System.out.println("🗑️ 成功删除 " + deletedCount + "/" + messageIds.size() + " 条消息");
    }
    
    /**
     * 发送私聊消息
     */
    public static void sendPrivateMessage(AbsSender bot, long userId, String text) {
        sendMessage(bot, userId, text);
    }
    
    /**
     * 格式化时间（秒转换为分钟:秒）
     */
    public static String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        }
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return minutes + "分" + (remainingSeconds > 0 ? remainingSeconds + "秒" : "");
    }
    
    /**
     * 检查字符串是否为空或空白
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * 生成玩家编号列表
     */
    public static String formatPlayerList(java.util.List<String> players) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < players.size(); i++) {
            sb.append(i + 1).append(". ").append(players.get(i));
            if (i < players.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}