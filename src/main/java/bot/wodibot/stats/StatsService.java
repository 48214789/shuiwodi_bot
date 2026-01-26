package bot.wodibot.stats;

import bot.wodibot.model.PlayerStats;
import bot.wodibot.utils.GameLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 胜率统计服务
 */
public class StatsService {

    private static final Map<Long, PlayerStats> playerStats = new ConcurrentHashMap<>();
    private static final String STATS_FILE = "player_stats.txt";
    private static final String BACKUP_DIR = "stats_backups";

    // 记录玩家最后游戏时间
    private static final Map<Long, Long> lastPlayTime = new ConcurrentHashMap<>();

    // 记录玩家连胜信息
    private static final Map<Long, Integer> winStreaks = new ConcurrentHashMap<>();
    private static final Map<Long, Integer> maxWinStreaks = new ConcurrentHashMap<>();
    private static final Map<Long, Long> lastGameResult = new ConcurrentHashMap<>(); // userId -> 0:输, 1:赢

    static {
        loadStats();
        createBackupDirectory();
    }

    /**
     * 获取玩家主页信息
     */
    public static synchronized String getPlayerProfile(long userId, String playerName) {
        PlayerStats stats = getPlayerStats(userId);
        if (stats == null || stats.getTotalGames() == 0) {
            return "📊 *" + playerName + " 的主页*\n\n" +
                    "🎮 这位玩家还没有开始游戏\n" +
                    "💡 使用 /startgame 开始第一局游戏吧！";
        }

        // 获取玩家名字带徽章
        String playerNameWithBadge = getPlayerNameWithBadge(userId, playerName);

        // 获取排名信息
        Map<String, Integer> ranks = getPlayerAllRanks(userId);
        int totalRank = ranks.get("total");
        int civilianRank = ranks.get("civilian");
        int undercoverRank = ranks.get("undercover");

        // 获取连胜信息
        int currentStreak = getCurrentWinStreak(userId);
        int maxStreak = getMaxWinStreak(userId);

        // 获取称号
        List<String> titles = getPlayerTitles(userId, stats);

        // 获取状态
        String status = getPlayerStatus(userId);

        // 构建主页
        StringBuilder profile = new StringBuilder();

        // 顶部边框
        profile.append("█               玩 家 荣 誉          █\n");
        profile.append("█▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀█\n");

        // 玩家信息
        String displayName = playerNameWithBadge.replace("👑", "").replace("🔥", "").replace("🛡️", "")
                .replace("🎭", "").replace("🥈", "").replace("🥉", "")
                .replace("🎮", "").replace("👤", "").replace("🌱", "").trim();
        profile.append("█         ▶️ 玩家：").append(displayName);
        // 对齐处理
        int nameLength = getDisplayLength(displayName);
        int spaces = 22 - nameLength; // 调整为22个字符宽度
        for (int i = 0; i < spaces; i++)
            profile.append(" ");
        profile.append("\n");

        profile.append("█                                 \n");

        // 巅峰排名（新的标题）
        profile.append("█       🏆 巅 峰 排 名 🏆           \n");

        // 根据排名动态显示，冠军用★突出
        List<String> rankLines = new ArrayList<>();

        // 卧底榜排名（优先显示冠军）
        if (undercoverRank > 0) {
            String rankSymbol = "";
            if (undercoverRank == 1) {
                rankSymbol = "★🥇";
            } else if (undercoverRank == 2) {
                rankSymbol = "★🥈";
            } else if (undercoverRank == 3) {
                rankSymbol = "★🥉";
            } else {
                rankSymbol = "  ";
            }

            String rankText = "";
            if (undercoverRank == 1) {
                rankText = String.format(" 卧底榜单 · 冠军");
            } else if (undercoverRank == 2) {
                rankText = String.format(" 卧底榜单 · 亚军");
            } else if (undercoverRank == 3) {
                rankText = String.format(" 卧底榜单 · 季军");
            } else {
                rankText = String.format(" 卧底榜单 · 排名#%d", undercoverRank);
            }

            rankLines.add(rankSymbol + rankText);
        } else {
            rankLines.add("★📊 平民榜单 · 未上榜");
        }

        // 总榜排名
        if (totalRank > 0) {
            String rankSymbol = "";
            if (totalRank == 1) {
                rankSymbol = "★🥇";
            } else if (totalRank == 2) {
                rankSymbol = "★🥈";
            } else if (totalRank == 3) {
                rankSymbol = "★🥉";
            } else {
                rankSymbol = "  ";
            }

            String rankText = "";
            if (totalRank == 1) {
                rankText = String.format(" 胜率总榜 · 冠军");
            } else if (totalRank == 2) {
                rankText = String.format(" 胜率总榜 · 亚军");
            } else if (totalRank == 3) {
                rankText = String.format(" 胜率总榜 · 季军");
            } else {
                rankText = String.format(" 胜率总榜 · 排名#%d", totalRank);
            }

            rankLines.add(rankSymbol + rankText);
        } else {
            rankLines.add("★📊 胜率总榜 · 未上榜");
        }

        // 平民榜排名
        if (civilianRank > 0) {
            String rankSymbol = "";
            if (civilianRank == 1) {
                rankSymbol = "★🥇";
            } else if (civilianRank == 2) {
                rankSymbol = "★🥈";
            } else if (civilianRank == 3) {
                rankSymbol = "★🥉";
            } else {
                rankSymbol = "  ";
            }

            String rankText = "";
            if (civilianRank == 1) {
                rankText = String.format(" 平民榜单 · 冠军");
            } else if (civilianRank == 2) {
                rankText = String.format(" 平民榜单 · 亚军");
            } else if (civilianRank == 3) {
                rankText = String.format(" 平民榜单 · 季军");
            } else {
                rankText = String.format(" 平民榜单 · 排名#%d", civilianRank);
            }

            rankLines.add(rankSymbol + rankText);
        } else {
            rankLines.add("★📊 平民榜单 · 未上榜");
        }

        // 显示排名信息（每行最多2个排名）
        for (int i = 0; i < rankLines.size(); i++) {
            String line = rankLines.get(i);
            profile.append("█   ").append(line);

            // 对齐处理
            int lineLength = getDisplayLength(
                    line.replace("★", "").replace("🥇", "").replace("🥈", "").replace("🥉", "").replace("📊", ""));
            int lineSpaces = 28 - lineLength; // 调整对齐宽度
            for (int j = 0; j < lineSpaces; j++)
                profile.append(" ");
            profile.append("\n");
        }

        profile.append("█                                 \n");

        // 个人荣耀展示
        profile.append("█      ✨ 个 人 荣 耀 展 示           \n");

        // 称号
        if (!titles.isEmpty()) {
            profile.append("█   │ 🎭 称号：").append(titles.get(0)).append("\n");
            for (int i = 1; i < titles.size(); i++) {
                profile.append("█                          ").append(titles.get(i)).append("\n");
            }
        } else {
            profile.append("█   │    🎭 称号：").append("暂无称号\n");
        }

        // 状态
        profile.append("█   │ ⭐ 状态：").append(status).append("\n");

        // 连胜记录
        String streakText;
        if (currentStreak > 0) {
            streakText = currentStreak + "连胜";
            if (currentStreak >= 10) {
                streakText += "🔥🔥";
            } else if (currentStreak >= 4) {
                streakText += "🔥";
            } else {
                streakText += "✨";
            }
        } else {
            streakText = "无连胜记录";
        }
        profile.append("█   │ ⚡ 连胜：").append(streakText).append("\n");

        profile.append("█                                 \n");

        // 核心战绩（修改标题）
        profile.append("█      📊 核 心 战 绩\n");

        // 总场次和胜率
        String totalInfo = String.format("总场次：%d场 | 胜率：%.1f%%",
                stats.getTotalGames(), stats.getWinRate());
        profile.append("█   ▸ ").append(totalInfo);
        int totalInfoLength = getDisplayLength(totalInfo);
        int totalSpaces = 25 - totalInfoLength;
        for (int i = 0; i < totalSpaces; i++)
            profile.append(" ");
        profile.append("\n");

        // 平民胜率
        String civilianInfo = String.format("平民胜率：%.1f%%", stats.getCivilianWinRate());
        profile.append("█   ▸ ").append(civilianInfo);
        int civilianInfoLength = getDisplayLength(civilianInfo);
        int civilianSpaces = 25 - civilianInfoLength;
        for (int i = 0; i < civilianSpaces; i++)
            profile.append(" ");
        profile.append("    \n");

        // 卧底胜率
        String undercoverInfo = String.format("卧底胜率：%.1f%%", stats.getUndercoverWinRate());
        profile.append("█   ▸ ").append(undercoverInfo);
        int undercoverInfoLength = getDisplayLength(undercoverInfo);
        int undercoverSpaces = 25 - undercoverInfoLength;
        for (int i = 0; i < undercoverSpaces; i++)
            profile.append(" ");
        profile.append("     \n");

        // 底部边框
        profile.append("▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀");

        return profile.toString();
    }

    /**
     * 获取显示长度（中文算2个字符，英文算1个）
     */
    private static int getDisplayLength(String str) {
        int length = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            // 检查是否是代理对的高代理部分（通常是emoji）
            if (Character.isHighSurrogate(c)) {
                length += 2; // emoji算2个字符宽度
                i++; // 跳过低代理部分
                continue;
            }

            // 检查是否是低代理部分（不应该单独出现，但安全起见）
            if (Character.isLowSurrogate(c)) {
                continue;
            }

            // 检查是否是全角字符（包括中文、日文、韩文等）
            if (isFullWidthCharacter(c)) {
                length += 2; // 全角字符算2个字符宽度
            } else {
                length += 1; // 半角字符算1个字符宽度
            }
        }
        return length;
    }

    /**
     * 判断是否是全角字符
     */
    private static boolean isFullWidthCharacter(char c) {
        // 中文、日文、韩文等CJK字符
        if (c >= '\u4e00' && c <= '\u9fff') {
            return true;
        }

        // 全角符号范围
        if (c >= '\u3000' && c <= '\u303f') {
            return true;
        }

        // 全角字母数字
        if (c >= '\uff01' && c <= '\uff5e') {
            return true;
        }

        // 全角空格
        if (c == '\u3000') {
            return true;
        }

        return false;
    }

    /**
     * 获取排名emoji
     */
    private static String getRankEmoji(int rank) {
        switch (rank) {
            case 1:
                return "🥇";
            case 2:
                return "🥈";
            case 3:
                return "🥉";
            default:
                return "  ";
        }
    }

    /**
     * 获取玩家称号列表
     */
    private static List<String> getPlayerTitles(long userId, PlayerStats stats) {
        List<String> titles = new ArrayList<>();

        // 卧底胜率 > 75%
        if (stats.getUndercoverGames() >= 5 && stats.getUndercoverWinRate() > 75) {
            titles.add("🎭 伪 装 大 师");
        }

        // 平民胜率 > 75%
        if (stats.getCivilianGames() >= 5 && stats.getCivilianWinRate() > 75) {
            titles.add("🛡️ 平 民 专 家");
        }

        // 双身份胜率都 > 65%
        if (stats.getCivilianGames() >= 5 && stats.getUndercoverGames() >= 5 &&
                stats.getCivilianWinRate() > 65 && stats.getUndercoverWinRate() > 65) {
            titles.add("👥 全 能 战 士");
        }

        // 双身份胜率都 > 70%
        if (stats.getCivilianGames() >= 5 && stats.getUndercoverGames() >= 5 &&
                stats.getCivilianWinRate() > 70 && stats.getUndercoverWinRate() > 70) {
            titles.add("⚔️  双 面 精 英");
        }

        // 连胜称号
        int maxStreak = getMaxWinStreak(userId);
        if (maxStreak >= 10) {
            titles.add("🔥 连 胜 之 神");
        } else if (maxStreak >= 4) {
            titles.add("⚡ 势 不 可 挡");
        } else if (maxStreak >= 1) {
            titles.add("💫 初 露 锋 芒");
        }

        // 游戏场次称号
        if (stats.getTotalGames() >= 30) {
            titles.add("👑 游 戏 传 奇");
        } else if (stats.getTotalGames() >= 15) {
            titles.add("🏆 游 戏 强 者");
        } else if (stats.getTotalGames() >= 5) {
            titles.add("🎮 游 戏 达 人");
        }

        return titles;
    }

    /**
     * 获取玩家状态
     */
    private static String getPlayerStatus(long userId) {
        PlayerStats stats = getPlayerStats(userId);
        if (stats == null) {
            return "新 手 上 路";
        }

        int totalGames = stats.getTotalGames();
        int currentStreak = getCurrentWinStreak(userId);

        if (totalGames >= 8) {
            return "手 感 火 热";
        } else if (totalGames > 5) {
            return "刚 刚 开 始";
        } else if (totalGames >= 3) {
            return "小 试 牛 刀";
        } else {
            return "新 手 上 路";
        }
    }

    /**
     * 获取当前连胜
     */
    private static int getCurrentWinStreak(long userId) {
        return winStreaks.getOrDefault(userId, 0);
    }

    /**
     * 获取最大连胜
     */
    private static int getMaxWinStreak(long userId) {
        return maxWinStreaks.getOrDefault(userId, 0);
    }

    /**
     * 更新连胜记录
     */
    private static void updateWinStreak(long userId, boolean win) {
        if (win) {
            // 赢了，连胜+1
            int currentStreak = winStreaks.getOrDefault(userId, 0) + 1;
            winStreaks.put(userId, currentStreak);

            // 更新最大连胜
            int maxStreak = maxWinStreaks.getOrDefault(userId, 0);
            if (currentStreak > maxStreak) {
                maxWinStreaks.put(userId, currentStreak);
            }
        } else {
            // 输了，连胜重置
            winStreaks.put(userId, 0);
        }

        // 记录最后游戏结果
        lastGameResult.put(userId, win ? 1L : 0L);
    }

    /**
     * 检查玩家是否是胜率排行榜第一名
     * 
     * @param userId 玩家ID
     * @return true如果是第一名，false如果不是
     */
    public static synchronized boolean isPlayerRankedFirst(long userId) {
        List<PlayerStats> leaderboard = getLeaderboard(0);
        if (leaderboard.isEmpty()) {
            return false;
        }

        PlayerStats topPlayer = leaderboard.get(0);
        return topPlayer.userId == userId;
    }

    /**
     * 检查玩家是否是平民胜率排行榜第一名
     * 
     * @param userId 玩家ID
     * @return true如果是第一名，false如果不是
     */
    public static synchronized boolean isPlayerCivilianRankedFirst(long userId) {
        List<PlayerStats> leaderboard = getCivilianLeaderboard(0);
        if (leaderboard.isEmpty()) {
            return false;
        }

        PlayerStats topPlayer = leaderboard.get(0);
        return topPlayer.userId == userId;
    }

    /**
     * 检查玩家是否是卧底胜率排行榜第一名
     * 
     * @param userId 玩家ID
     * @return true如果是第一名，false如果不是
     */
    public static synchronized boolean isPlayerUndercoverRankedFirst(long userId) {
        List<PlayerStats> leaderboard = getUndercoverLeaderboard(0);
        if (leaderboard.isEmpty()) {
            return false;
        }

        PlayerStats topPlayer = leaderboard.get(0);
        return topPlayer.userId == userId;
    }

    /**
     * 检查玩家是否获得了新称号
     * 返回新增的称号列表
     */
    public static synchronized List<String> checkNewTitles(long userId, PlayerStats newStats) {
        List<String> newTitles = new ArrayList<>();
        PlayerStats oldStats = getPlayerStats(userId);

        if (oldStats == null || oldStats.getTotalGames() == 0) {
            // 第一次游戏，不检查称号
            return newTitles;
        }

        // 检查各项条件是否达到称号要求
        checkTitlesByCondition(userId, oldStats, newStats, newTitles);

        return newTitles;
    }

    /**
     * 检查称号条件
     */
    private static void checkTitlesByCondition(long userId, PlayerStats oldStats, PlayerStats newStats,
            List<String> newTitles) {
        // 卧底胜率 > 75% 且场次足够
        if (oldStats.getUndercoverWinRate() <= 75 && newStats.getUndercoverWinRate() > 75 &&
                newStats.getUndercoverGames() >= 5) {
            newTitles.add("🎭 伪 装 大 师");
        }

        // 平民胜率 > 75% 且场次足够
        if (oldStats.getCivilianWinRate() <= 75 && newStats.getCivilianWinRate() > 75 &&
                newStats.getCivilianGames() >= 5) {
            newTitles.add("🛡️ 平 民 专 家");
        }

        // 双身份胜率都 > 65%
        if ((oldStats.getCivilianWinRate() <= 65 || oldStats.getUndercoverWinRate() <= 65) &&
                newStats.getCivilianWinRate() > 65 && newStats.getUndercoverWinRate() > 65 &&
                newStats.getCivilianGames() >= 5 && newStats.getUndercoverGames() >= 5) {
            newTitles.add("👥 全 能 战 士");
        }

        // 双身份胜率都 > 70%
        if ((oldStats.getCivilianWinRate() <= 70 || oldStats.getUndercoverWinRate() <= 70) &&
                newStats.getCivilianWinRate() > 70 && newStats.getUndercoverWinRate() > 70 &&
                newStats.getCivilianGames() >= 5 && newStats.getUndercoverGames() >= 5) {
            newTitles.add("⚔️  双 面 精 英");
        }

        // 连胜称号
        int oldMaxStreak = getMaxWinStreak(userId);
        int currentStreak = getCurrentWinStreak(userId);

        if (currentStreak >= 10 && oldMaxStreak < 10) {
            newTitles.add("🔥 连 胜 之 神");
        } else if (currentStreak >= 4 && oldMaxStreak < 4) {
            newTitles.add("⚡ 势 不 可 挡");
        } else if (currentStreak >= 1 && oldMaxStreak < 1) {
            newTitles.add("💫 初 露 锋 芒");
        }

        // 游戏场次称号
        if (oldStats.getTotalGames() < 5 && newStats.getTotalGames() >= 5) {
            newTitles.add("🎮 游 戏 达 人");
        }
        if (oldStats.getTotalGames() < 15 && newStats.getTotalGames() >= 15) {
            newTitles.add("🏆 游 戏 强 者");
        }
        if (oldStats.getTotalGames() < 30 && newStats.getTotalGames() >= 30) {
            newTitles.add("👑 游 戏 传 奇");
        }
    }

    /**
     * 获取玩家的所有称号
     */
    public static synchronized List<String> getAllPlayerTitles(long userId) {
        PlayerStats stats = getPlayerStats(userId);
        if (stats == null) {
            return new ArrayList<>();
        }
        return getPlayerTitles(userId, stats);
    }

    /**
     * 获取玩家在所有排行榜中的排名
     * 
     * @param userId 玩家ID
     * @return Map包含各种排名信息，键为排行榜类型，值为排名（1-based），未上榜返回-1
     */
    public static synchronized Map<String, Integer> getPlayerAllRanks(long userId) {
        Map<String, Integer> ranks = new HashMap<>();

        // 总胜率排名
        List<PlayerStats> leaderboard = getLeaderboard(0);
        ranks.put("total", getPlayerRankInList(leaderboard, userId));

        // 平民胜率排名
        List<PlayerStats> civilianLeaderboard = getCivilianLeaderboard(0);
        ranks.put("civilian", getPlayerRankInList(civilianLeaderboard, userId));

        // 卧底胜率排名
        List<PlayerStats> undercoverLeaderboard = getUndercoverLeaderboard(0);
        ranks.put("undercover", getPlayerRankInList(undercoverLeaderboard, userId));

        return ranks;
    }

    /**
     * 在排行榜列表中查找玩家的排名
     * 
     * @param leaderboard 排行榜列表
     * @param userId      玩家ID
     * @return 排名（1-based），未上榜返回-1
     */
    private static int getPlayerRankInList(List<PlayerStats> leaderboard, long userId) {
        if (leaderboard.isEmpty()) {
            return -1;
        }

        for (int i = 0; i < leaderboard.size(); i++) {
            if (leaderboard.get(i).userId == userId) {
                return i + 1;
            }
        }

        return -1;
    }

    /**
     * 获取玩家的等级标识
     * 
     * @param userId 玩家ID
     * @return 等级标识字符串
     */
    public static synchronized String getPlayerRankBadge(long userId) {
        boolean isTotalFirst = isPlayerRankedFirst(userId);
        boolean isCivilianFirst = isPlayerCivilianRankedFirst(userId);
        boolean isUndercoverFirst = isPlayerUndercoverRankedFirst(userId);

        if (isTotalFirst) {
            return "👑"; // 总榜第一 - 皇冠
        } else if (isCivilianFirst && isUndercoverFirst) {
            return "🔥"; // 双榜第一 - 火焰
        } else if (isCivilianFirst) {
            return "🛡️"; // 平民第一 - 盾牌
        } else if (isUndercoverFirst) {
            return "🎭"; // 卧底第一 - 面具
        }

        Map<String, Integer> ranks = getPlayerAllRanks(userId);
        int totalRank = ranks.get("total");

        if (totalRank > 0 && totalRank <= 3) {
            switch (totalRank) {
                case 2:
                    return "🥈"; // 总榜第二 - 银牌
                case 3:
                    return "🥉"; // 总榜第三 - 铜牌
            }
        }

        PlayerStats stats = getPlayerStats(userId);
        if (stats != null) {
            if (stats.getTotalGames() >= 10) {
                return "🎮"; // 资深玩家 - 游戏手柄
            } else if (stats.getTotalGames() > 0) {
                return "👤"; // 普通玩家 - 人像
            }
        }

        return ""; // 新玩家或没有标识
    }

    /**
     * 获取玩家带标识的名字
     * 
     * @param userId     玩家ID
     * @param playerName 玩家名字
     * @return 带标识的名字
     */
    public static synchronized String getPlayerNameWithBadge(long userId, String playerName) {
        String badge = getPlayerRankBadge(userId);
        if (!badge.isEmpty()) {
            return badge + " " + playerName;
        }
        return playerName;
    }

    /**
     * 获取玩家详细等级说明
     * 
     * @param userId 玩家ID
     * @return 等级说明
     */
    public static synchronized String getPlayerRankDescription(long userId) {
        boolean isTotalFirst = isPlayerRankedFirst(userId);
        boolean isCivilianFirst = isPlayerCivilianRankedFirst(userId);
        boolean isUndercoverFirst = isPlayerUndercoverRankedFirst(userId);
        Map<String, Integer> ranks = getPlayerAllRanks(userId);
        PlayerStats stats = getPlayerStats(userId);

        if (isTotalFirst) {
            return "👑 胜率总榜冠军";
        } else if (isCivilianFirst && isUndercoverFirst) {
            return "🔥 双榜第一（平民+卧底）";
        } else if (isCivilianFirst) {
            return "🛡️ 平民榜冠军";
        } else if (isUndercoverFirst) {
            return "🎭 卧底榜冠军";
        }

        int totalRank = ranks.get("total");
        if (totalRank > 0) {
            if (totalRank == 2)
                return "🥈 胜率总榜亚军";
            if (totalRank == 3)
                return "🥉 胜率总榜季军";
            if (totalRank <= 10)
                return "⭐ 胜率总榜第" + totalRank + "名";
        }

        if (stats != null) {
            if (stats.getTotalGames() >= 20) {
                return "🎮 资深玩家（" + stats.getTotalGames() + "场）";
            } else if (stats.getTotalGames() >= 10) {
                return "🎮 老玩家（" + stats.getTotalGames() + "场）";
            } else if (stats.getTotalGames() > 0) {
                return "👤 普通玩家（" + stats.getTotalGames() + "场）";
            }
        }

        return "🌱 新玩家";
    }

    /**
     * 记录游戏结果（修改原有方法，添加连胜记录）
     */
    public static synchronized Map<Long, List<String>> recordGameResultAndCheckTitles(
            Map<Long, String> players,
            Map<Long, Boolean> playerRoles,
            boolean undercoverWin) {

        Map<Long, List<String>> newTitlesMap = new HashMap<>();

        for (Map.Entry<Long, String> entry : players.entrySet()) {
            long userId = entry.getKey();
            String playerName = entry.getValue();
            Boolean isUndercover = playerRoles.get(userId);

            if (isUndercover == null) {
                GameLogger.logError(-1, "无法获取玩家角色: " + playerName);
                continue;
            }

            // 获取更新前的统计数据
            PlayerStats oldStats = getPlayerStats(userId);
            if (oldStats == null) {
                oldStats = new PlayerStats(userId, playerName);
                playerStats.put(userId, oldStats);
            } else {
                // 创建旧数据的副本用于比较
                oldStats = createStatsCopy(oldStats);
            }

            // 判断该玩家是否胜利
            boolean playerWin = (isUndercover && undercoverWin) || (!isUndercover && !undercoverWin);

            // 更新连胜记录
            updateWinStreak(userId, playerWin);

            // 获取或创建玩家统计
            PlayerStats stats = playerStats.computeIfAbsent(userId,
                    id -> new PlayerStats(id, playerName));

            // 更新玩家姓名（可能用户改了名字）
            stats.playerName = playerName;

            // 记录游戏结果
            stats.recordGame(playerWin, isUndercover);

            // 检查新称号
            List<String> newTitles = checkNewTitles(userId, stats);
            if (!newTitles.isEmpty()) {
                newTitlesMap.put(userId, newTitles);
                GameLogger.logPlayerAction(-1, userId, playerName,
                        "获得新称号: " + String.join(", ", newTitles));
            }

            // 更新最后游戏时间
            lastPlayTime.put(userId, System.currentTimeMillis());

            GameLogger.logPlayerAction(-1, userId, playerName,
                    "战绩更新: " + (playerWin ? "胜利" : "失败") +
                            " 身份: " + (isUndercover ? "卧底" : "平民") +
                            " 当前连胜: " + getCurrentWinStreak(userId));
        }

        // 保存到文件
        saveStats();

        return newTitlesMap;
    }

    /**
     * 创建统计数据的副本
     */
    private static PlayerStats createStatsCopy(PlayerStats stats) {
        PlayerStats copy = new PlayerStats(stats.userId, stats.playerName);
        copy.setTotalGames(stats.getTotalGames());
        copy.setWins(stats.getWins());
        copy.setLosses(stats.getLosses());
        copy.setCivilianGames(stats.getCivilianGames());
        copy.setCivilianWins(stats.getCivilianWins());
        copy.setUndercoverGames(stats.getUndercoverGames());
        copy.setUndercoverWins(stats.getUndercoverWins());
        return copy;
    }

    /**
     * 获取玩家个人胜率
     */
    public static PlayerStats getPlayerStats(long userId) {
        return playerStats.get(userId);
    }

    /**
     * 获取玩家最后游戏时间
     */
    public static Long getLastPlayTime(long userId) {
        return lastPlayTime.get(userId);
    }

    /**
     * 获取所有玩家胜率排行榜
     * 
     * @param limit 返回前多少名，0表示返回所有
     * @return 排序后的玩家列表
     */
    public static List<PlayerStats> getLeaderboard(int limit) {
        List<PlayerStats> allStats = new ArrayList<>(playerStats.values());

        // 过滤掉游戏次数太少的玩家（少于3场）
        allStats.removeIf(stats -> stats.getTotalGames() < 3);

        if (allStats.isEmpty()) {
            return new ArrayList<>();
        }

        // 按胜率排序，胜率相同按游戏次数排序，再相同按ID排序
        allStats.sort((a, b) -> {
            // 先按胜率降序
            int winRateCompare = Double.compare(b.getWinRate(), a.getWinRate());
            if (winRateCompare != 0)
                return winRateCompare;

            // 胜率相同按游戏场次降序
            int gamesCompare = Integer.compare(b.getTotalGames(), a.getTotalGames());
            if (gamesCompare != 0)
                return gamesCompare;

            // 游戏场次相同按ID升序
            return Long.compare(a.userId, b.userId);
        });

        if (limit > 0 && limit < allStats.size()) {
            return allStats.subList(0, limit);
        }
        return allStats;
    }

    /**
     * 获取平民胜率排行榜
     */
    public static List<PlayerStats> getCivilianLeaderboard(int limit) {
        List<PlayerStats> allStats = new ArrayList<>(playerStats.values());

        // 过滤掉平民游戏次数太少的玩家（少于2场）
        allStats.removeIf(stats -> stats.getCivilianGames() < 2);

        if (allStats.isEmpty()) {
            return new ArrayList<>();
        }

        // 按平民胜率排序
        allStats.sort((a, b) -> {
            // 先按平民胜率降序
            int civilianRateCompare = Double.compare(b.getCivilianWinRate(), a.getCivilianWinRate());
            if (civilianRateCompare != 0)
                return civilianRateCompare;

            // 胜率相同按平民游戏场次降序
            int gamesCompare = Integer.compare(b.getCivilianGames(), a.getCivilianGames());
            if (gamesCompare != 0)
                return gamesCompare;

            // 游戏场次相同按ID升序
            return Long.compare(a.userId, b.userId);
        });

        if (limit > 0 && limit < allStats.size()) {
            return allStats.subList(0, limit);
        }
        return allStats;
    }

    /**
     * 获取卧底胜率排行榜
     */
    public static List<PlayerStats> getUndercoverLeaderboard(int limit) {
        List<PlayerStats> allStats = new ArrayList<>(playerStats.values());

        // 过滤掉卧底游戏次数太少的玩家（少于2场）
        allStats.removeIf(stats -> stats.getUndercoverGames() < 2);

        if (allStats.isEmpty()) {
            return new ArrayList<>();
        }

        // 按卧底胜率排序
        allStats.sort((a, b) -> {
            // 先按卧底胜率降序
            int undercoverRateCompare = Double.compare(b.getUndercoverWinRate(), a.getUndercoverWinRate());
            if (undercoverRateCompare != 0)
                return undercoverRateCompare;

            // 胜率相同按卧底游戏场次降序
            int gamesCompare = Integer.compare(b.getUndercoverGames(), a.getUndercoverGames());
            if (gamesCompare != 0)
                return gamesCompare;

            // 游戏场次相同按ID升序
            return Long.compare(a.userId, b.userId);
        });

        if (limit > 0 && limit < allStats.size()) {
            return allStats.subList(0, limit);
        }
        return allStats;
    }

    /**
     * 从文件加载统计数据（修改版，加载连胜数据）
     */
    public static synchronized void loadStats() {
        File statsFile = new File(STATS_FILE);
        if (!statsFile.exists()) {
            System.out.println("📊 胜率文件不存在，将创建新文件");
            return;
        }

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(statsFile), StandardCharsets.UTF_8))) {

            playerStats.clear();
            lastPlayTime.clear();
            winStreaks.clear();
            maxWinStreaks.clear();
            lastGameResult.clear();

            int loadedCount = 0;
            String line;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                try {
                    String[] parts = line.split(",");
                    if (parts.length >= 9) {
                        long userId = Long.parseLong(parts[0]);
                        String playerName = parts[1];

                        PlayerStats stats = new PlayerStats(userId, playerName);
                        stats.setTotalGames(Integer.parseInt(parts[2]));
                        stats.setWins(Integer.parseInt(parts[3]));
                        stats.setLosses(Integer.parseInt(parts[4]));
                        stats.setCivilianGames(Integer.parseInt(parts[5]));
                        stats.setCivilianWins(Integer.parseInt(parts[6]));
                        stats.setUndercoverGames(Integer.parseInt(parts[7]));
                        stats.setUndercoverWins(Integer.parseInt(parts[8]));

                        playerStats.put(userId, stats);

                        // 最后游戏时间
                        if (parts.length >= 10 && !parts[9].isEmpty()) {
                            long lastTime = Long.parseLong(parts[9]);
                            lastPlayTime.put(userId, lastTime);
                        }

                        // 连胜数据（新格式）
                        if (parts.length >= 12) {
                            int currentStreak = Integer.parseInt(parts[10]);
                            int maxStreak = Integer.parseInt(parts[11]);
                            winStreaks.put(userId, currentStreak);
                            maxWinStreaks.put(userId, maxStreak);
                        }

                        loadedCount++;
                    }
                } catch (Exception e) {
                    System.err.println("❌ 解析胜率记录失败: " + line);
                    e.printStackTrace();
                }
            }

            System.out.println("📊 加载了 " + loadedCount + " 条玩家胜率记录（含连胜数据）");

        } catch (IOException e) {
            System.err.println("❌ 加载胜率文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 保存统计数据到文件（修改版，保存连胜数据）
     */
    static synchronized void saveStats() {
        try {
            // 备份当前文件
            backupStatsFile();

            try (BufferedWriter bw = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(STATS_FILE), StandardCharsets.UTF_8))) {

                bw.write("# 玩家胜率统计文件\n");
                bw.write(
                        "# 格式: userId,playerName,totalGames,wins,losses,civilianGames,civilianWins,undercoverGames,undercoverWins[,lastPlayTime,currentStreak,maxStreak]\n");

                for (PlayerStats stats : playerStats.values()) {
                    Long lastTime = lastPlayTime.get(stats.userId);
                    int currentStreak = winStreaks.getOrDefault(stats.userId, 0);
                    int maxStreak = maxWinStreaks.getOrDefault(stats.userId, 0);

                    String line = String.format("%d,%s,%d,%d,%d,%d,%d,%d,%d,%s,%d,%d",
                            stats.userId,
                            stats.playerName,
                            stats.getTotalGames(),
                            stats.getWins(),
                            stats.getLosses(),
                            stats.getCivilianGames(),
                            stats.getCivilianWins(),
                            stats.getUndercoverGames(),
                            stats.getUndercoverWins(),
                            lastTime != null ? lastTime.toString() : "0",
                            currentStreak,
                            maxStreak);
                    bw.write(line);
                    bw.newLine();
                }

                System.out.println("📊 保存了 " + playerStats.size() + " 条玩家胜率记录（含连胜数据）");

            } catch (IOException e) {
                System.err.println("❌ 保存胜率文件失败: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (Exception e) {
            System.err.println("❌ 备份胜率文件失败: " + e.getMessage());
        }
    }

    /**
     * 备份统计数据文件
     */
    private static void backupStatsFile() throws IOException {
        File backupDir = new File(BACKUP_DIR);
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        File statsFile = new File(STATS_FILE);
        if (!statsFile.exists()) {
            return;
        }

        // 每天只保留一个备份
        String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File backupFile = new File(backupDir, "player_stats_" + dateStr + ".txt");

        if (!backupFile.exists()) {
            Files.copy(statsFile.toPath(), backupFile.toPath());
            System.out.println("📊 已创建胜率文件备份: " + backupFile.getName());
        }
    }

    /**
     * 创建备份目录
     */
    private static void createBackupDirectory() {
        File backupDir = new File(BACKUP_DIR);
        if (!backupDir.exists()) {
            backupDir.mkdirs();
            System.out.println("📁 创建胜率备份目录: " + BACKUP_DIR);
        }
    }

    /**
     * 获取统计数据总数
     */
    public static int getStatsCount() {
        return playerStats.size();
    }

    /**
     * 获取活跃玩家数量（30天内游戏过的）
     */
    public static int getActivePlayerCount() {
        long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
        int activeCount = 0;

        for (Map.Entry<Long, Long> entry : lastPlayTime.entrySet()) {
            if (entry.getValue() > thirtyDaysAgo) {
                activeCount++;
            }
        }

        return activeCount;
    }

    /**
     * 清理长期不活跃的玩家数据（超过90天）
     */
    public static synchronized void cleanupOldStats() {
        long ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000);
        int removedCount = 0;

        Iterator<Map.Entry<Long, Long>> iterator = lastPlayTime.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> entry = iterator.next();
            if (entry.getValue() < ninetyDaysAgo) {
                // 只删除游戏次数少于5场的玩家
                PlayerStats stats = playerStats.get(entry.getKey());
                if (stats != null && stats.getTotalGames() < 5) {
                    playerStats.remove(entry.getKey());
                    iterator.remove();
                    removedCount++;
                    System.out.println("🧹 清理不活跃玩家: " + stats.playerName +
                            " (最后游戏: " + formatTime(entry.getValue()) + ")");
                }
            }
        }

        if (removedCount > 0) {
            saveStats();
            System.out.println("🧹 清理了 " + removedCount + " 个不活跃玩家数据");
        }
    }

    /**
     * 格式化成可读时间
     */
    private static String formatTime(long timestamp) {
        Date date = new Date(timestamp);
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(date);
    }

    /**
     * 重置所有统计数据（危险操作）
     */
    public static synchronized void resetAllStats() {
        playerStats.clear();
        lastPlayTime.clear();
        saveStats();
        System.out.println("⚠️ 所有胜率数据已重置");
    }

    /**
     * 导出统计数据为CSV格式
     */
    public static synchronized String exportStatsToCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("玩家ID,玩家名称,总游戏数,胜利数,失败数,总胜率,平民游戏数,平民胜利数,平民胜率,卧底游戏数,卧底胜利数,卧底胜率,最后游戏时间\n");

        for (PlayerStats stats : playerStats.values()) {
            Long lastTime = lastPlayTime.get(stats.userId);
            String lastTimeStr = lastTime != null ? formatTime(lastTime) : "从未游戏";

            csv.append(String.format("%d,\"%s\",%d,%d,%d,%.2f%%,%d,%d,%.2f%%,%d,%d,%.2f%%,%s\n",
                    stats.userId,
                    stats.playerName,
                    stats.getTotalGames(),
                    stats.getWins(),
                    stats.getLosses(),
                    stats.getWinRate(),
                    stats.getCivilianGames(),
                    stats.getCivilianWins(),
                    stats.getCivilianWinRate(),
                    stats.getUndercoverGames(),
                    stats.getUndercoverWins(),
                    stats.getUndercoverWinRate(),
                    lastTimeStr));
        }

        return csv.toString();
    }

    /**
     * 强制保存统计数据（供外部调用）
     */
    public static synchronized void forceSaveStats() {
        saveStats();
    }
}