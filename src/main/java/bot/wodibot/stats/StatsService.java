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
    
    static {
        loadStats();
        createBackupDirectory();
    }

    /**
     * 检查玩家是否是胜率排行榜第一名
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
     * 获取玩家在所有排行榜中的排名
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
     * @param leaderboard 排行榜列表
     * @param userId 玩家ID
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
        switch(totalRank) {
            case 2: return "🥈"; // 总榜第二 - 银牌
            case 3: return "🥉"; // 总榜第三 - 铜牌
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
 * @param userId 玩家ID
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
        if (totalRank == 2) return "🥈 胜率总榜亚军";
        if (totalRank == 3) return "🥉 胜率总榜季军";
        if (totalRank <= 10) return "⭐ 胜率总榜第" + totalRank + "名";
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
     * 记录游戏结果
     */
    public static synchronized void recordGameResult(
            Map<Long, String> players, 
            Map<Long, Boolean> playerRoles, // userId -> isUndercover
            boolean undercoverWin) {
        
        for (Map.Entry<Long, String> entry : players.entrySet()) {
            long userId = entry.getKey();
            String playerName = entry.getValue();
            Boolean isUndercover = playerRoles.get(userId);
            
            if (isUndercover == null) {
                GameLogger.logError(-1, "无法获取玩家角色: " + playerName);
                continue;
            }
            
            // 判断该玩家是否胜利
            boolean playerWin = (isUndercover && undercoverWin) || (!isUndercover && !undercoverWin);
            
            // 获取或创建玩家统计
            PlayerStats stats = playerStats.computeIfAbsent(userId, 
                id -> new PlayerStats(id, playerName));
            
            // 更新玩家姓名（可能用户改了名字）
            stats.playerName = playerName;
            
            // 记录游戏结果
            stats.recordGame(playerWin, isUndercover);
            
            // 更新最后游戏时间
            lastPlayTime.put(userId, System.currentTimeMillis());
            
            GameLogger.logPlayerAction(-1, userId, playerName, 
                "战绩更新: " + (playerWin ? "胜利" : "失败") + 
                " 身份: " + (isUndercover ? "卧底" : "平民"));
        }
        
        // 保存到文件
        saveStats();
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
            if (winRateCompare != 0) return winRateCompare;
            
            // 胜率相同按游戏场次降序
            int gamesCompare = Integer.compare(b.getTotalGames(), a.getTotalGames());
            if (gamesCompare != 0) return gamesCompare;
            
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
            if (civilianRateCompare != 0) return civilianRateCompare;
            
            // 胜率相同按平民游戏场次降序
            int gamesCompare = Integer.compare(b.getCivilianGames(), a.getCivilianGames());
            if (gamesCompare != 0) return gamesCompare;
            
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
            if (undercoverRateCompare != 0) return undercoverRateCompare;
            
            // 胜率相同按卧底游戏场次降序
            int gamesCompare = Integer.compare(b.getUndercoverGames(), a.getUndercoverGames());
            if (gamesCompare != 0) return gamesCompare;
            
            // 游戏场次相同按ID升序
            return Long.compare(a.userId, b.userId);
        });
        
        if (limit > 0 && limit < allStats.size()) {
            return allStats.subList(0, limit);
        }
        return allStats;
    }
    
    /**
     * 从文件加载统计数据（改为public，供外部调用）
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
            lastPlayTime.clear(); // 清除旧的最后游戏时间
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
                        
                        // 如果有最后游戏时间字段（第10个字段）
                        if (parts.length >= 10 && !parts[9].isEmpty()) {
                            long lastTime = Long.parseLong(parts[9]);
                            lastPlayTime.put(userId, lastTime);
                        }
                        
                        loadedCount++;
                    }
                } catch (Exception e) {
                    System.err.println("❌ 解析胜率记录失败: " + line);
                    e.printStackTrace();
                }
            }
            
            System.out.println("📊 加载了 " + loadedCount + " 条玩家胜率记录");
            
        } catch (IOException e) {
            System.err.println("❌ 加载胜率文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 保存统计数据到文件（改为package-private）
     */
    static synchronized void saveStats() {
        try {
            // 备份当前文件
            backupStatsFile();
            
            try (BufferedWriter bw = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(STATS_FILE), StandardCharsets.UTF_8))) {
                
                bw.write("# 玩家胜率统计文件\n");
                bw.write("# 格式: userId,playerName,totalGames,wins,losses,civilianGames,civilianWins,undercoverGames,undercoverWins[,lastPlayTime]\n");
                
                for (PlayerStats stats : playerStats.values()) {
                    Long lastTime = lastPlayTime.get(stats.userId);
                    String line = String.format("%d,%s,%d,%d,%d,%d,%d,%d,%d%s",
                        stats.userId,
                        stats.playerName,
                        stats.getTotalGames(),
                        stats.getWins(),
                        stats.getLosses(),
                        stats.getCivilianGames(),
                        stats.getCivilianWins(),
                        stats.getUndercoverGames(),
                        stats.getUndercoverWins(),
                        lastTime != null ? "," + lastTime : ""
                    );
                    bw.write(line);
                    bw.newLine();
                }
                
                System.out.println("📊 保存了 " + playerStats.size() + " 条玩家胜率记录");
                
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
                lastTimeStr
            ));
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