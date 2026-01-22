package bot.wodibot.game;

public enum GameState {
    IDLE,        // 空闲状态
    JOINING,     // 加入阶段
    SPEAKING,    // 发言阶段
    VOTING,      // 投票阶段
    ENDED        // 游戏结束
}