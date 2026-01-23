package bot.wodibot.model;

/**
 * 玩家胜率统计
 */
public class PlayerStats {
    public long userId;
    public String playerName;
    
    // 游戏统计数据
    private int totalGames = 0;      // 总游戏数
    private int wins = 0;            // 胜利次数
    private int losses = 0;          // 失败次数
    
    // 身份统计数据
    private int civilianGames = 0;   // 平民身份游戏数
    private int civilianWins = 0;    // 平民身份胜利数
    private int undercoverGames = 0; // 卧底身份游戏数
    private int undercoverWins = 0;  // 卧底身份胜利数
    
    public PlayerStats(long userId, String playerName) {
        this.userId = userId;
        this.playerName = playerName;
    }
    
    // 更新游戏结果
    public void recordGame(boolean win, boolean isUndercover) {
        totalGames++;
        if (win) {
            wins++;
        } else {
            losses++;
        }
        
        if (isUndercover) {
            undercoverGames++;
            if (win) undercoverWins++;
        } else {
            civilianGames++;
            if (win) civilianWins++;
        }
    }
    
    // 计算总胜率
    public double getWinRate() {
        if (totalGames == 0) return 0.0;
        return (double) wins / totalGames * 100;
    }
    
    // 计算平民胜率
    public double getCivilianWinRate() {
        if (civilianGames == 0) return 0.0;
        return (double) civilianWins / civilianGames * 100;
    }
    
    // 计算卧底胜率
    public double getUndercoverWinRate() {
        if (undercoverGames == 0) return 0.0;
        return (double) undercoverWins / undercoverGames * 100;
    }
    
    // Getters
    public int getTotalGames() { return totalGames; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public int getCivilianGames() { return civilianGames; }
    public int getCivilianWins() { return civilianWins; }
    public int getUndercoverGames() { return undercoverGames; }
    public int getUndercoverWins() { return undercoverWins; }
    
    // 格式化胜率显示
    public String getFormattedStats() {
        return String.format(
            "👤 玩家: %s\n" +
            "📊 总战绩: %d 战 %d 胜 %d 负 (胜率: %.1f%%)\n" +
            "👤 平民: %d 战 %d 胜 (胜率: %.1f%%)\n" +
            "🎭 卧底: %d 战 %d 胜 (胜率: %.1f%%)",
            playerName,
            totalGames, wins, losses, getWinRate(),
            civilianGames, civilianWins, getCivilianWinRate(),
            undercoverGames, undercoverWins, getUndercoverWinRate()
        );
    }
    
    // 为StatsService访问添加setter方法
    public void setTotalGames(int totalGames) { this.totalGames = totalGames; }
    public void setWins(int wins) { this.wins = wins; }
    public void setLosses(int losses) { this.losses = losses; }
    public void setCivilianGames(int civilianGames) { this.civilianGames = civilianGames; }
    public void setCivilianWins(int civilianWins) { this.civilianWins = civilianWins; }
    public void setUndercoverGames(int undercoverGames) { this.undercoverGames = undercoverGames; }
    public void setUndercoverWins(int undercoverWins) { this.undercoverWins = undercoverWins; }
}