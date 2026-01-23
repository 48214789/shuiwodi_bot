package bot.wodibot;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class BotConfig {
    private static final Properties props = new Properties();
    private static boolean loaded = false;
    private static boolean valid = false;
    
    static {
        reload();
    }
    
    public static synchronized void reload() {
        try {
            // 在Replit中，资源文件路径不同
            File configFile = new File("src/main/resources/bot.properties");
            
            if (configFile.exists()) {
                try (InputStream is = new FileInputStream(configFile)) {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8));
                    
                    props.clear();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        
                        int equalsIndex = line.indexOf('=');
                        if (equalsIndex > 0) {
                            String key = line.substring(0, equalsIndex).trim();
                            String value = line.substring(equalsIndex + 1).trim();
                            props.setProperty(key, value);
                        }
                    }
                    
                    System.out.println("✅ 从文件加载配置: " + configFile.getAbsolutePath());
                    valid = validateConfig();
                    loaded = true;
                }
            } else {
                System.err.println("⚠️ 配置文件不存在: " + configFile.getAbsolutePath());
                System.err.println("当前工作目录: " + new File(".").getAbsolutePath());
                loadDefaults();
                valid = true;
                loaded = true;
            }
        } catch (Exception e) {
            System.err.println("❌ 加载配置文件失败: " + e.getMessage());
            loadDefaults();
            valid = true;
            loaded = true;
        }
    }
    
    private static boolean validateConfig() {
        try {
            // 验证Bot Token
            String token = props.getProperty("bot.token");
            if (token == null || token.isEmpty() || token.equals("YOUR_BOT_TOKEN")) {
                System.err.println("❌ Bot Token未配置或为默认值");
                return false;
            }
            
            // 验证管理员ID
            String adminIds = props.getProperty("admin.ids", "");
            if (adminIds.isEmpty()) {
                System.err.println("⚠️ 未配置管理员ID");
            }
            
            // 验证游戏配置
            int maxPlayers = Integer.parseInt(props.getProperty("game.max.players", "12"));
            int minPlayers = Integer.parseInt(props.getProperty("game.min.players", "3"));
            
            if (minPlayers < 2) {
                System.err.println("❌ 最小玩家数不能小于2，调整为2");
                props.setProperty("game.min.players", "2");
                minPlayers = 2;
            }
            
            if (maxPlayers > 20) {
                System.err.println("⚠️ 最大玩家数超过20，可能影响性能");
            }
            
            if (minPlayers >= maxPlayers) {
                System.err.println("❌ 最小玩家数应小于最大玩家数，调整配置");
                props.setProperty("game.min.players", "3");
                props.setProperty("game.max.players", "12");
            }
            
            // 验证时间配置
            int speakingTime = Integer.parseInt(props.getProperty("game.speaking.time", "60"));
            int votingTime = Integer.parseInt(props.getProperty("game.voting.time", "30"));
            int joinTime = Integer.parseInt(props.getProperty("game.join.time", "30"));
            
            if (speakingTime < 10) {
                System.err.println("❌ 发言时间太短，调整为最小值30秒");
                props.setProperty("game.speaking.time", "30");
            }
            
            if (votingTime < 10) {
                System.err.println("❌ 投票时间太短，调整为最小值15秒");
                props.setProperty("game.voting.time", "15");
            }
            
            if (joinTime < 10) {
                System.err.println("❌ 加入时间太短，调整为最小值20秒");
                props.setProperty("game.join.time", "20");
            }
            
            // 验证卧底比例
            double undercoverRatio = Double.parseDouble(props.getProperty("game.undercover.ratio", "0.33"));
            if (undercoverRatio < 0.1 || undercoverRatio > 0.5) {
                System.err.println("❌ 卧底比例应在0.1-0.5之间，调整为0.33");
                props.setProperty("game.undercover.ratio", "0.33");
            }
            
            // 验证词库重载间隔
            int reloadInterval = Integer.parseInt(props.getProperty("words.reload.interval", "5"));
            if (reloadInterval < 1) {
                System.err.println("❌ 词库重载间隔太短，调整为5分钟");
                props.setProperty("words.reload.interval", "5");
            }
            
            return true;
            
        } catch (NumberFormatException e) {
            System.err.println("❌ 数值配置格式错误: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("❌ 配置验证失败: " + e.getMessage());
            return false;
        }
    }
    
    private static void loadDefaults() {
        props.clear();
        props.setProperty("bot.token", "8225083954:AAGymegzlCKECIaqMxKyYqFzwiWWddbnmGg");
        props.setProperty("bot.username", "UndercoverWordGameBot");
        props.setProperty("admin.ids", "6906663525,646844463");
        props.setProperty("game.max.players", "12");
        props.setProperty("game.min.players", "3");
        props.setProperty("game.speaking.time", "60");
        props.setProperty("game.voting.time", "30");
        props.setProperty("game.join.time", "30");
        props.setProperty("game.undercover.ratio", "0.33");
        props.setProperty("words.reload.interval", "5");
        props.setProperty("words.file", "words.txt");
        props.setProperty("log.level", "INFO");
    }
    
    // 配置检查
    public static boolean isLoaded() { 
        return loaded; 
    }
    
    public static boolean isValid() { 
        return valid; 
    }
    
    // 获取配置状态
    public static String getConfigStatus() {
        StringBuilder status = new StringBuilder();
        status.append("📋 配置状态:\n");
        status.append("已加载: ").append(isLoaded() ? "✅" : "❌").append("\n");
        status.append("有效: ").append(isValid() ? "✅" : "❌").append("\n");
        status.append("配置项数量: ").append(props.size()).append("\n");
        return status.toString();
    }
    
    // Bot配置
    public static String getBotToken() {
        return props.getProperty("bot.token");
    }
    
    public static String getBotUsername() {
        return props.getProperty("bot.username");
    }
    
    // 管理员配置
    public static String[] getAdminIds() {
        String ids = props.getProperty("admin.ids", "");
        if (ids.isEmpty()) {
            return new String[0];
        }
        return ids.split(",");
    }
    
    public static boolean isAdmin(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        for (String adminId : getAdminIds()) {
            if (adminId.trim().equals(userId.trim())) {
                return true;
            }
        }
        return false;
    }
    
    // 游戏配置
    public static int getMaxPlayers() {
        try {
            return Integer.parseInt(props.getProperty("game.max.players", "12"));
        } catch (NumberFormatException e) {
            return 12;
        }
    }
    
    public static int getMinPlayers() {
        try {
            return Integer.parseInt(props.getProperty("game.min.players", "3"));
        } catch (NumberFormatException e) {
            return 3;
        }
    }
    
    public static int getSpeakingTime() {
        try {
            return Integer.parseInt(props.getProperty("game.speaking.time", "60"));
        } catch (NumberFormatException e) {
            return 60;
        }
    }
    
    public static int getVotingTime() {
        try {
            return Integer.parseInt(props.getProperty("game.voting.time", "30"));
        } catch (NumberFormatException e) {
            return 30;
        }
    }
    
    public static int getJoinTime() {
        try {
            return Integer.parseInt(props.getProperty("game.join.time", "30"));
        } catch (NumberFormatException e) {
            return 30;
        }
    }
    
    public static double getUndercoverRatio() {
        try {
            return Double.parseDouble(props.getProperty("game.undercover.ratio", "0.33"));
        } catch (NumberFormatException e) {
            return 0.33;
        }
    }
    
    // 词库配置
    public static String getWordFile() {
        return props.getProperty("words.file", "words.txt");
    }
    
    public static int getWordReloadInterval() {
        try {
            return Integer.parseInt(props.getProperty("words.reload.interval", "5"));
        } catch (NumberFormatException e) {
            return 5;
        }
    }
    
    public static String getLogLevel() {
        return props.getProperty("log.level", "INFO");
    }
}