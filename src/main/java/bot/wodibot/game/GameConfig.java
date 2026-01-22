package bot.wodibot.game;

import bot.wodibot.BotConfig;

public class GameConfig {
    private final int maxPlayers;
    private final int minPlayers;
    private final int speakingTime;
    private final int votingTime;
    private final int joinTime;
    private final double undercoverRatio;
    
    public GameConfig() {
        this.maxPlayers = BotConfig.getMaxPlayers();
        this.minPlayers = BotConfig.getMinPlayers();
        this.speakingTime = BotConfig.getSpeakingTime();
        this.votingTime = BotConfig.getVotingTime();
        this.joinTime = BotConfig.getJoinTime();
        this.undercoverRatio = BotConfig.getUndercoverRatio();
    }
    
    // 也可以创建自定义配置
    public GameConfig(int maxPlayers, int minPlayers, int speakingTime, 
                     int votingTime, int joinTime, double undercoverRatio) {
        this.maxPlayers = maxPlayers;
        this.minPlayers = minPlayers;
        this.speakingTime = speakingTime;
        this.votingTime = votingTime;
        this.joinTime = joinTime;
        this.undercoverRatio = undercoverRatio;
    }
    
    // Getters
    public int getMaxPlayers() { return maxPlayers; }
    public int getMinPlayers() { return minPlayers; }
    public int getSpeakingTime() { return speakingTime; }
    public int getVotingTime() { return votingTime; }
    public int getJoinTime() { return joinTime; }
    public double getUndercoverRatio() { return undercoverRatio; }
}