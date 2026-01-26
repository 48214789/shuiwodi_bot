package bot.wodibot.game;

import bot.wodibot.BotConfig;
import bot.wodibot.model.Player;
import bot.wodibot.utils.BotUtils;
import bot.wodibot.utils.GameLogger;
import bot.wodibot.word.WordService;
import bot.wodibot.stats.StatsService;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.*;
import java.util.concurrent.*;

public class GameRoom {

    private final long chatId;
    private final GameConfig config;
    private GameState state = GameState.IDLE;

    private final Map<Long, Player> players = new LinkedHashMap<>();
    private final Map<Long, Long> votes = new HashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> currentTask;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new HashMap<>();

    private String civilianWord;
    private String undercoverWord;
    private int round = 1;

    private Integer joinMessageId;
    private final List<String> gameLog = new ArrayList<>();

    // 存储机器人发送的消息ID，用于游戏开始时的清理
    private final List<Integer> botMessageIds = new ArrayList<>();

    // 敏感词列表（简单示例，实际应该从配置文件加载）
    private static final String[] SENSITIVE_WORDS = {
            "脏话", "骂人", "侮辱",
            "网址", "http://", "https://", "@"
    };

    public GameRoom(long chatId) {
        this.chatId = chatId;
        this.config = new GameConfig();
        GameLogger.logGame(chatId, "游戏房间已创建");
    }

    public GameRoom(long chatId, GameConfig config) {
        this.chatId = chatId;
        this.config = config;
        GameLogger.logGame(chatId, "游戏房间已创建（自定义配置）");
    }

    /* ================= 消息管理方法 ================= */

    /**
     * 记录机器人发送的消息ID
     */
    private void recordBotMessageId(Integer messageId) {
        if (messageId != null && !botMessageIds.contains(messageId)) {
            botMessageIds.add(messageId);
        }
    }

    /**
     * 清除所有机器人消息
     */
    private void clearAllBotMessages(AbsSender bot) {
        if (botMessageIds.isEmpty()) {
            return;
        }

        GameLogger.logGame(chatId, "开始清理机器人所有消息，共" + botMessageIds.size() + "条");

        // 复制列表以避免并发修改
        List<Integer> messagesToDelete = new ArrayList<>(botMessageIds);

        // 批量删除消息
        BotUtils.deleteMessages(bot, chatId, messagesToDelete);

        // 清空记录
        botMessageIds.clear();

        GameLogger.logGame(chatId, "消息清理完成");
    }

    /**
     * 发送消息并记录ID
     */
    private void sendAndRecordMessage(AbsSender bot, String text) {
        Integer messageId = BotUtils.sendMessageWithId(bot, chatId, text);
        if (messageId != null) {
            recordBotMessageId(messageId);
        }
    }

    /* ================= 消息入口 ================= */

    public synchronized void onMessage(AbsSender bot, Message msg) {
        if (msg.getText() == null || msg.getFrom().getIsBot())
            return;

        long uid = msg.getFrom().getId();
        String text = msg.getText().trim();
        String userName = msg.getFrom().getFirstName();

        GameLogger.logPlayerAction(chatId, uid, userName, "发送消息: " +
                (text.length() > 20 ? text.substring(0, 20) + "..." : text));

        if ("/start".equals(text)) {
            BotUtils.sendMessage(bot, chatId, "🚀 欢迎使用本机器人，祝您天天开心！");
            return;
        }

        if ("/startgame".equals(text) || "/startgame@shuiwodi_bot".equals(text)) {
            if (state == GameState.IDLE || state == GameState.ENDED) {
                // 开始新游戏前清理之前的所有机器人消息
                clearAllBotMessages(bot);
                start(bot);
            } else {
                sendAndRecordMessage(bot, "⚠️ 游戏正在进行中，请等待当前游戏结束");
            }
            return;
        }

        if ("/help".equals(text) || "/help@shuiwodi_bot".equals(text)) {
            sendHelp(bot);
            return;
        }

        if ("/status".equals(text) || "/status@shuiwodi_bot".equals(text)) {
            sendGameStatus(bot);
            return;
        }

        if ("/players".equals(text) || "/players@shuiwodi_bot".equals(text)) {
            sendPlayerList(bot);
            return;
        }

        if ("/rules".equals(text) || "/rules@shuiwodi_bot".equals(text)) {
            sendRules(bot);
            return;
        }

        if ("/cancel".equals(text) || "/cancel@shuiwodi_bot".equals(text)) {
            cancelGame(bot);
            return;
        }

        // 原有的文字加入逻辑（保持兼容）
        if (state == GameState.JOINING) {
            updateJoinMessage(bot);
            return;
        }

        // 发言阶段
        if (state == GameState.SPEAKING && msg.getText().startsWith("s")) {
            handleSpeaking(bot, uid, msg.getText());
            return;
        }

        // 投票阶段
        if (state == GameState.VOTING) {
            handleVoting(bot, uid, text);
            return;
        }
    }

    /* ================= 按钮回调处理 ================= */

    public synchronized void onCallbackJoin(AbsSender bot, long userId, String userName,
            int messageId, CallbackQuery callbackQuery) {
        if (state != GameState.JOINING) {
            sendAndRecordMessage(bot, "⚠️ 游戏不在加入阶段");
            return;
        }

        if (joinMessageId == null) {
            joinMessageId = messageId;
            recordBotMessageId(messageId);
        }

        // 检查是否已加入
        if (players.containsKey(userId)) {
            BotUtils.sendPrivateMessage(bot, userId, "⚠️ 你已经加入游戏了");
            return;
        }

        // 检查人数上限
        if (players.size() >= config.getMaxPlayers()) {
            BotUtils.sendPrivateMessage(bot, userId,
                    "❌ 房间已满（最多" + config.getMaxPlayers() + "人）");
            return;
        }

        Player newPlayer = new Player(userId, userName);
        players.put(userId, newPlayer);

        // 获取带标识的玩家名字
        String playerNameWithBadge = StatsService.getPlayerNameWithBadge(userId, userName);
        String rankDescription = StatsService.getPlayerRankDescription(userId);

        GameLogger.logPlayerAction(chatId, userId, userName, "加入了游戏 - " + rankDescription);

        String welcomeMessage;
        if (rankDescription.contains("冠军") || rankDescription.contains("双榜第一")) {
            // 冠军级玩家
            welcomeMessage = getChampionWelcomeMessage(userName, rankDescription);
        } else if (rankDescription.contains("亚军") || rankDescription.contains("季军")) {
            // 前三名玩家
            welcomeMessage = getTopThreeWelcomeMessage(userName, rankDescription);
        } else if (rankDescription.contains("资深") || rankDescription.contains("老玩家")) {
            // 资深玩家
            welcomeMessage = getVeteranWelcomeMessage(userName, rankDescription);
        } else {
            // 普通玩家
            welcomeMessage = getRegularPlayerWelcomeMessage(userName, rankDescription);
        }

        sendAndRecordMessage(bot, welcomeMessage);
        updateJoinMessage(bot);
    }

    /**
     * 获取冠军玩家的欢迎消息
     */
    private String getChampionWelcomeMessage(String playerNameWithBadge, String rankDescription) {
        String[] messages = {
                // 1. 王者风范
                "👑 *【至尊驾到】*  " + playerNameWithBadge + " （" + rankDescription +
                        "），亲临游戏！全体玩家肃然起敬，恭迎王者归来！✨\n" +
                        "⚜️ 此等强者驾临，今日战局必将是传奇之战！",

                // 2. 天神降临
                "✨ *【神级降临】* 天象异变，众星朝拜！ " + playerNameWithBadge + " （" + rankDescription +
                        "），已降临战场！\n" +
                        "💫 服务器检测到传说级玩家，全体玩家获得【观战荣耀】Buff！",

                // 3. 宗门老祖
                "🏯 *【老祖出关】* 隐世千年的" + rankDescription + "级大能—— " + playerNameWithBadge +
                        "  今日破关而出！\n" +
                        "⚔️ 后生晚辈们，此乃观摩学习的千载良机，还不速来拜见！",

                // 4. 星际统帅
                "🚀 *【统帅登陆】* 最高权限确认！银河系" + rankDescription + "级指挥官—— " + playerNameWithBadge +
                        "  已接管游戏！\n" +
                        "🛡️ 全体玩家进入【敬畏模式】，请展现你们的最佳表现！",

                // 5. 九霄帝君
                "🌌 *【帝君临凡】* 九霄之上，帝座移位！ " + playerNameWithBadge + " （" + rankDescription +
                        "），携无上威压驾临！\n" +
                        "👁️ 此等存在，一举一动皆是法则，一言一语皆是天机！"
        };
        return messages[new Random().nextInt(messages.length)];
    }

    /**
     * 获取前三名玩家的欢迎消息
     */
    private String getTopThreeWelcomeMessage(String playerNameWithBadge, String rankDescription) {
        String[] messages = {
                // 1. 一代宗师
                "⚔️ *【宗师驾临】* 纵横江湖多年的" + rankDescription + "级强者—— " + playerNameWithBadge +
                        "  今日再现武林！\n" +
                        "📜 其名号在玩家间传颂已久，今日有幸同场竞技，实乃幸事！",

                // 2. 殿堂元老
                "🏛️ *【殿堂元老】* 游戏殿堂级人物 " + playerNameWithBadge + " （" + rankDescription +
                        "），载誉归来！\n" +
                        "🎖️ 无数辉煌战绩加身，今日之战必将再添传奇！",

                // 3. 名宿登场
                "🌟 *【一方名宿】* 久负盛名的" + rankDescription + "级玩家—— " + playerNameWithBadge +
                        "  加入战局！\n" +
                        "🔥 其战术智慧与丰富经验，足以让任何对手心生敬畏！"
        };
        return messages[new Random().nextInt(messages.length)];
    }

    /**
     * 获取资深玩家的欢迎消息
     */
    private String getVeteranWelcomeMessage(String playerNameWithBadge, String rankDescription) {
        String[] messages = {
                "🎮 *经验丰富！* " + playerNameWithBadge +
                        "（" + rankDescription + "）再次加入！",
                "🎮 *老司机！* " + playerNameWithBadge +
                        " 回归游戏！（" + rankDescription + "）",
                "🎮 *数据达人！* " + playerNameWithBadge +
                        " 加入战斗！（" + rankDescription + "）"
        };
        return messages[new Random().nextInt(messages.length)];
    }

    /**
     * 获取普通玩家的欢迎消息
     */
    private String getRegularPlayerWelcomeMessage(String playerNameWithBadge, String rankDescription) {
        String[] messages = {
                "👤 " + playerNameWithBadge + " 加入游戏！",
                "👤 " + playerNameWithBadge + " 准备战斗！",
                "👤 " + playerNameWithBadge + " 已就位！"
        };
        return messages[new Random().nextInt(messages.length)];
    }

    public synchronized void onCallbackStart(AbsSender bot, long userId, int messageId) {
        if (state != GameState.JOINING) {
            sendAndRecordMessage(bot, "⚠️ 游戏不在加入阶段");
            return;
        }

        Player player = players.get(userId);
        if (player == null) {
            sendAndRecordMessage(bot, "❌ 只有已加入的玩家才能开始游戏");
            return;
        }

        assign(bot);
    }

    /* ================= 游戏流程 ================= */

    private void start(AbsSender bot) {
        cancelAllTasks();
        players.clear();
        votes.clear();
        gameLog.clear();
        state = GameState.JOINING;
        joinMessageId = null;
        round = 1;

        InlineKeyboardMarkup keyboardMarkup = createJoinKeyboard();

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(getJoinMessageText());
        message.setParseMode("Markdown");
        message.setReplyMarkup(keyboardMarkup);

        try {
            Message sentMessage = bot.execute(message);
            joinMessageId = sentMessage.getMessageId();
            recordBotMessageId(sentMessage.getMessageId());
            GameLogger.logGame(chatId, "游戏开始招募玩家");
        } catch (Exception e) {
            GameLogger.logError(chatId, "发送消息失败: " + e.getMessage());
            e.printStackTrace();
        }

        // 使用配置的时间
        scheduleTask("join_timeout", () -> {
            synchronized (this) {
                if (state == GameState.JOINING) {
                    GameLogger.logGame(chatId, "加入阶段超时，自动开始游戏");
                    assign(bot);
                }
            }
        }, config.getJoinTime(), TimeUnit.SECONDS);
    }

    private void cancelGame(AbsSender bot) {
        if (state == GameState.IDLE || state == GameState.ENDED) {
            sendAndRecordMessage(bot, "⚠️ 没有进行中的游戏可以取消");
            return;
        }

        cancelAllTasks();
        players.clear();
        votes.clear();
        state = GameState.ENDED;

        // 清理所有机器人消息
        clearAllBotMessages(bot);

        sendAndRecordMessage(bot, "🛑 游戏已取消");
        GameLogger.logGame(chatId, "游戏被用户取消");
    }

    private InlineKeyboardMarkup createJoinKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton joinButton = new InlineKeyboardButton();
        joinButton.setText("✅ 加入游戏");
        joinButton.setCallbackData("join_game");
        row1.add(joinButton);

        keyboard.add(row1);
        keyboardMarkup.setKeyboard(keyboard);

        return keyboardMarkup;
    }

    private String getJoinMessageText() {
        StringBuilder playerList = new StringBuilder();
        int i = 1;
        for (Player p : players.values()) {
            String playerNameWithBadge = StatsService.getPlayerNameWithBadge(p.userId, p.name);
            playerList.append(i++).append(". ").append(playerNameWithBadge);
            playerList.append("\n");
        }

        int totalCount = players.size();
        int minPlayers = config.getMinPlayers();

        return "🎮 *谁是卧底游戏*\n\n" +
                "👥 已加入玩家 (" + totalCount + "/" + minPlayers + "):\n" +
                (playerList.length() > 0 ? playerList.toString() : "(暂无)\n\n") +
                "⏰ " + BotUtils.formatTime(config.getJoinTime()) + "后自动开始\n" +
                "✅ 点击按钮加入游戏\n\n" +
                "⚠️ *注意*: 首次加入需要先私聊机器人发送 `/start` \n" +
                "⚠️ *注意*: 首次加入需要先私聊机器人发送 `/start` \n" +
                "⚠️ *注意*: 首次加入需要先私聊机器人发送 `/start` \n" +
                "             ⏳ 否则收不到您的身份！！！";
    }

    private void updateJoinMessage(AbsSender bot) {
        if (joinMessageId == null) {
            System.out.println("joinMessageId 为 null，无法更新消息");
            return;
        }

        try {
            String messageText = getJoinMessageText();
            InlineKeyboardMarkup keyboardMarkup = createJoinKeyboard();

            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(String.valueOf(chatId));
            editMessage.setMessageId(joinMessageId);
            editMessage.setText(messageText);
            editMessage.setParseMode("Markdown");
            editMessage.setReplyMarkup(keyboardMarkup);

            bot.execute(editMessage);
            GameLogger.logGame(chatId, "更新加入消息，当前人数: " + players.size());

        } catch (Exception e) {
            GameLogger.logError(chatId, "更新消息失败: " + e.getMessage());
        }
    }

    private void assign(AbsSender bot) {
        cancelAllTasks();

        // 清理加入阶段的所有消息
        clearAllBotMessages(bot);

        if (players.size() < config.getMinPlayers()) {
            sendAndRecordMessage(bot,
                    "❌ 人数不足，游戏取消\n" +
                            "👥 当前人数: " + players.size() + " 人\n" +
                            "✅ 需要至少 " + config.getMinPlayers() + " 人才能开始游戏\n\n" +
                            "📢 请邀请更多朋友加入后再试！");
            state = GameState.ENDED;
            return;
        }

        WordService.WordPair pair = WordService.randomForChat(chatId);
        if (pair == null) {
            sendAndRecordMessage(bot,
                    "❌ 词库加载失败\n" +
                            "📦 无法获取游戏词语，请稍后重试\n" +
                            "🔄 你可以尝试重新开始游戏");
            state = GameState.ENDED;
            return;
        }

        civilianWord = pair.civilians.get(0);
        undercoverWord = pair.undercover;

        // 使用配置的比例计算卧底数量
        int undercoverCount = Math.max(1, (int) Math.ceil(players.size() * config.getUndercoverRatio()));
        List<Player> playerList = new ArrayList<>(players.values());
        Collections.shuffle(playerList);

        StringBuilder roleInfo = new StringBuilder();
        roleInfo.append("🎉 *游戏开始！身份分配完成*\n\n");
        roleInfo.append("📊 *游戏信息*\n");
        roleInfo.append("👥 玩家人数: ").append(players.size()).append(" 人\n");
        roleInfo.append("🎭 卧底数量: ").append(undercoverCount).append(" 人\n");
        roleInfo.append("📈 卧底比例: ").append(String.format("%.0f%%", config.getUndercoverRatio() * 100)).append("\n\n");

        roleInfo.append("📋 *玩家列表*\n");
        for (int i = 0; i < playerList.size(); i++) {
            Player p = playerList.get(i);
            if (i < undercoverCount) {
                p.undercover = true;
                p.word = undercoverWord;
                BotUtils.sendPrivateMessage(bot, p.userId,
                        "🎭 你是 *卧底*\n📝 你的词语是: *" + undercoverWord + "*\n\n" +
                                "💡 *发言提示*：\n" +
                                "1. 描述要具体但不要太明显\n" +
                                "2. 卧底要模仿平民的描述方式\n" +
                                "3. 平民要寻找描述中的不一致\n" +
                                "4. 使用 `s描述` 来发言\n\n" +
                                "5. 使用 `s描述` 来发言\n\n" +
                                "6. 使用 `s描述` 来发言\n\n" +
                                "⏰ 你有" + config.getSpeakingTime() + "秒时间发言");
            } else {
                p.undercover = false;
                p.word = civilianWord;
                BotUtils.sendPrivateMessage(bot, p.userId,
                        "👤 你是 *平民*\n📝 你的词语是: *" + civilianWord + "*\n\n" +
                                "💡 提示: 找出描述不一致的卧底！");
            }
            p.alive = true;
            p.spoke = "";
            p.voted = false;

            roleInfo.append((i + 1)).append(". ").append(StatsService.getPlayerNameWithBadge(p.userId, p.name));
            roleInfo.append("\n");
        }
        roleInfo.append("\n");
        roleInfo.append("\n");
        sendAndRecordMessage(bot, roleInfo.toString());
        GameLogger.logGame(chatId,
                "身份分配完成，平民词: " + civilianWord + "，卧底词: " + undercoverWord +
                        "，卧底数: " + undercoverCount + "/" + players.size());

        // 开始第一轮发言
        state = GameState.SPEAKING;

        String speakingStartMsg = "🗣 *发言阶段开始*\n\n" +
                "⏱ *发言时间*: " + config.getSpeakingTime() + " 秒\n" +
                "📝 *发言格式*:  `s描述` \n" +
                "📝 *发言格式*:  `s描述` \n" +
                "📝 *发言格式*:  `s描述` \n" +
                "🎯 *发言目标*: 描述你的词语，但不要直接说出\n\n" +
                "📌 *重要规则*\n" +
                "1. 每人只能发言一次\n" +
                "2. 发言要简洁明了\n" +
                "3. 不要直接说出词语\n" +
                "4. 发言重复别人的描述，这属于划水哦~\n\n" +
                "⏰ 倒计时开始...";

        sendAndRecordMessage(bot, speakingStartMsg);

        scheduleTask("speaking_timeout", () -> startVote(bot),
                config.getSpeakingTime(), TimeUnit.SECONDS);
    }

    private void handleSpeaking(AbsSender bot, long uid, String text) {
        Player p = players.get(uid);
        if (p != null && p.alive) {
            String description = text.replace("s", "").trim();

            // 检查描述是否有效
            if (BotUtils.isBlank(description)) {
                BotUtils.sendPrivateMessage(bot, uid, "⚠️ 描述不能为空，请重新发言");
                return;
            }

            if (description.length() > 50) {
                BotUtils.sendPrivateMessage(bot, uid, "⚠️ 描述过长，请控制在50字以内");
                return;
            }

            // 检查敏感词
            if (containsSensitiveWord(description)) {
                BotUtils.sendPrivateMessage(bot, uid, "⚠️ 描述包含不适当内容，请重新发言");
                GameLogger.logPlayerAction(chatId, uid, p.name, "发言包含敏感词: " + description);
                return;
            }

            p.spoke = description;
            BotUtils.sendPrivateMessage(bot, uid, "✅ 你的描述已记录: " + p.spoke);
            GameLogger.logPlayerAction(chatId, uid, p.name, "已发言: " + p.spoke);
        }

        // 检查是否所有玩家都已发言
        boolean isOver = true;
        for (Player player : players.values()) {
            if (player.alive && BotUtils.isBlank(player.spoke)) {
                isOver = false;
                break;
            }
        }

        if (isOver) {
            sendAndRecordMessage(bot, "✅ 所有玩家已完成发言");
            cancelTask("speaking_timeout");
            startVote(bot);
        }
    }

    private void startVote(AbsSender bot) {
        cancelAllTasks();
        state = GameState.VOTING;
        votes.clear();
        players.values().forEach(p -> p.voted = false);

        StringBuilder sb = new StringBuilder("🗳 *投票阶段* (第" + round + "轮)\n\n");
        sb.append("⏰ 时间: ").append(BotUtils.formatTime(config.getVotingTime())).append("\n\n");
        sb.append("玩家列表:\n");

        int i = 1;
        Map<Integer, Long> voteIndexMap = new HashMap<>();

        for (Player p : players.values()) {
            if (p.alive) {
                // 使用带标识的名字
                String playerNameWithBadge = StatsService.getPlayerNameWithBadge(p.userId, p.name);
                sb.append(i).append("、 ").append(playerNameWithBadge).append(" - ");
                if (!BotUtils.isBlank(p.spoke)) {
                    sb.append(p.spoke);
                } else {
                    sb.append("(未发言)");
                }
                sb.append("\n");
                voteIndexMap.put(i, p.userId);
                i++;
            }
        }

        sb.append("\n💡 *投票方式*:\n");
        sb.append("发送 `数字` 进行投票\n");
        sb.append("例如: `1` 投票给1号玩家");

        sendAndRecordMessage(bot, sb.toString());
        GameLogger.logGame(chatId, "第" + round + "轮投票开始");

        scheduleTask("voting_timeout", () -> finishVote(bot),
                config.getVotingTime(), TimeUnit.SECONDS);
    }

    private void handleVoting(AbsSender bot, long uid, String text) {
        try {
            String cleanText = text.replaceAll("[^0-9]", "").trim();
            if (cleanText.isEmpty()) {
                return;
            }

            int voteNumber = Integer.parseInt(cleanText);
            Player voter = players.get(uid);

            if (voter == null || !voter.alive || voter.voted) {
                return;
            }

            // 获取活着的玩家映射
            Map<Integer, Long> voteIndexMap = new HashMap<>();
            int index = 1;
            for (Player player : players.values()) {
                if (player.alive) {
                    voteIndexMap.put(index, player.userId);
                    index++;
                }
            }

            if (voteNumber < 1 || voteNumber > voteIndexMap.size()) {
                BotUtils.sendPrivateMessage(bot, uid,
                        "⚠️ 请输入有效的玩家编号（1-" + voteIndexMap.size() + "）");
                return;
            }

            long targetUserId = voteIndexMap.get(voteNumber);
            Player targetPlayer = players.get(targetUserId);

            if (targetPlayer == null || !targetPlayer.alive) {
                BotUtils.sendPrivateMessage(bot, uid, "⚠️ 不能投票给该玩家");
                return;
            }

            // 检查是否投给自己
            // if (targetUserId == uid) {
            // BotUtils.sendPrivateMessage(bot, uid, "⚠️ 不能投票给自己");
            // return;
            // }

            // 记录投票
            votes.put(uid, targetUserId);
            voter.voted = true;

            BotUtils.sendPrivateMessage(bot, uid, "✅ 你已投票给 " + targetPlayer.name);
            GameLogger.logPlayerAction(chatId, uid, voter.name,
                    "投票给 " + targetPlayer.name + " (" + targetUserId + ")");

            // 检查投票完成情况
            int votedCount = 0;
            int totalAlive = 0;
            for (Player p : players.values()) {
                if (p.alive) {
                    totalAlive++;
                    if (p.voted) {
                        votedCount++;
                    }
                }
            }

            if (votedCount == totalAlive) {
                sendAndRecordMessage(bot, "🎉 所有玩家已完成投票！正在计算结果...");
                cancelTask("voting_timeout");
                scheduler.submit(() -> finishVote(bot));
            }

        } catch (NumberFormatException e) {
            // 忽略非数字输入
        }
    }

    private void finishVote(AbsSender bot) {
        GameLogger.logGame(chatId, "开始计算投票结果");
        System.out.println("当前投票记录: " + votes);

        cancelAllTasks();

        if (votes.isEmpty()) {
            sendAndRecordMessage(bot, "⚠️ 本轮无人投票，重新发言");
            resetRoundForNewSpeech();
            scheduleTask("speaking_timeout", () -> startVote(bot),
                    config.getSpeakingTime(), TimeUnit.SECONDS);
            return;
        }

        // 统计票数
        Map<Long, Integer> count = new HashMap<>();
        for (Map.Entry<Long, Long> vote : votes.entrySet()) {
            Long voterId = vote.getKey();
            Long targetId = vote.getValue();
            Player targetPlayer = players.get(targetId);

            if (targetPlayer != null && targetPlayer.alive) {
                count.put(targetId, count.getOrDefault(targetId, 0) + 1);
                Player voter = players.get(voterId);
                GameLogger.logPlayerAction(chatId, voterId, voter.name,
                        "投票统计: " + targetPlayer.name + " +1票");
            }
        }

        if (count.isEmpty()) {
            sendAndRecordMessage(bot, "⚠️ 所有投票都投给了已淘汰玩家，重新发言");
            resetRoundForNewSpeech();
            scheduleTask("speaking_timeout", () -> startVote(bot),
                    config.getSpeakingTime(), TimeUnit.SECONDS);
            return;
        }

        // 找出最高票数
        int max = count.values().stream()
                .max(Integer::compare)
                .orElse(0);

        // 找出所有得票最高的玩家
        List<Long> eliminated = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : count.entrySet()) {
            if (e.getValue() == max) {
                eliminated.add(e.getKey());
            }
        }

        // 处理平票
        if (eliminated.size() > 1) {
            StringBuilder sb = new StringBuilder("⚠️ *平票*，以下玩家得票相同（");
            sb.append(max).append("票）:\n");

            for (Long playerId : eliminated) {
                sb.append("• ").append(players.get(playerId).name).append("\n");
            }
            sb.append("重新发言");

            sendAndRecordMessage(bot, sb.toString());
            GameLogger.logGame(chatId, "第" + round + "轮投票平票，重新发言");

            resetRoundForNewSpeech();
            scheduleTask("speaking_timeout", () -> startVote(bot),
                    config.getSpeakingTime(), TimeUnit.SECONDS);
            return;
        }

        // 淘汰玩家
        long out = eliminated.get(0);
        Player eliminatedPlayer = players.get(out);
        eliminatedPlayer.alive = false;

        sendAndRecordMessage(bot,
                "🚫 " + eliminatedPlayer.name + " 被淘汰（得票 " + max + "）");
        GameLogger.logGame(chatId,
                eliminatedPlayer.name + " 被淘汰，身份: " +
                        (eliminatedPlayer.undercover ? "卧底" : "平民"));

        // 检查游戏是否结束
        checkEnd(bot);
    }

    private void resetRoundForNewSpeech() {
        round++;
        votes.clear();
        players.values().forEach(p -> {
            p.spoke = "";
            p.voted = false;
        });
        state = GameState.SPEAKING;
    }

    private void checkEnd(AbsSender bot) {
        long aliveUndercover = players.values().stream()
                .filter(p -> p.alive && p.undercover)
                .count();
        long aliveCivilian = players.values().stream()
                .filter(p -> p.alive && !p.undercover)
                .count();

        if (aliveUndercover == 0) {
            sendAndRecordMessage(bot, "🎉 *平民胜利！*");
            GameLogger.logGame(chatId, "游戏结束，平民胜利");
            showGameSummary(bot, false);

            // 记录胜率 - 平民胜利
            recordGameStats(false);

            state = GameState.ENDED;
        } else if (aliveUndercover >= aliveCivilian) {
            sendAndRecordMessage(bot, "💀 *卧底胜利！*");
            GameLogger.logGame(chatId, "游戏结束，卧底胜利");
            showGameSummary(bot, true);

            // 记录胜率 - 卧底胜利
            recordGameStats(true);

            state = GameState.ENDED;
        } else {
            round++;
            players.values().forEach(p -> {
                p.spoke = "";
                p.voted = false;
            });
            state = GameState.SPEAKING;
            sendAndRecordMessage(bot, "🔄 第 " + round + " 轮开始");
            scheduleTask("speaking_timeout", () -> startVote(bot),
                    config.getSpeakingTime(), TimeUnit.SECONDS);
        }
    }

    /**
     * 记录游戏结果到胜率系统
     * 
     * @param undercoverWin 是否为卧底胜利
     */
    private void recordGameStats(boolean undercoverWin) {
        try {
            // 准备玩家数据
            Map<Long, String> playerMap = new HashMap<>();
            Map<Long, Boolean> roleMap = new HashMap<>();

            for (Player p : players.values()) {
                playerMap.put(p.userId, p.name);
                roleMap.put(p.userId, p.undercover);
            }

            // 记录胜率
            StatsService.recordGameResult(playerMap, roleMap, undercoverWin);

            GameLogger.logGame(chatId, "已记录本局游戏的胜率数据");

        } catch (Exception e) {
            GameLogger.logError(chatId, "记录胜率失败: " + e.getMessage());
        }
    }

    /* ================= 辅助方法 ================= */

    private void showGameSummary(AbsSender bot, boolean undercoverWin) {
        StringBuilder summary = new StringBuilder("🎮 *游戏回顾*\n\n");

        summary.append("🏆 ").append(undercoverWin ? "卧底胜利！" : "平民胜利！").append("\n\n");

        summary.append("📊 *身份分布*:\n");
        players.values().forEach(p -> {
            // 使用带标识的名字
            String playerNameWithBadge = StatsService.getPlayerNameWithBadge(p.userId, p.name);
            summary.append(playerNameWithBadge).append(" - ");
            if (p.undercover)
                summary.append("卧底");
            else
                summary.append("平民");
            if (!p.alive)
                summary.append(" (被淘汰)");
            summary.append("\n");
        });

        summary.append("\n📝 *词语*:\n");
        summary.append("平民词：").append(civilianWord).append("\n");
        summary.append("卧底词：").append(undercoverWord).append("\n");

        summary.append("\n⏱️ *游戏统计*:\n");
        summary.append("总轮数：").append(round).append("\n");
        summary.append("玩家数：").append(players.size()).append("\n");

        // 添加胜率提示
        summary.append("\n📈 *胜率已更新*\n");
        summary.append("使用 /p 查看你的胜率\n");
        summary.append("使用 /ph 查看胜率排行榜");

        sendAndRecordMessage(bot, summary.toString());
    }

    private void sendGameStatus(AbsSender bot) {
        StringBuilder status = new StringBuilder("📊 *游戏状态*\n\n");
        status.append("状态: ").append(state).append("\n");
        status.append("玩家数: ").append(players.size()).append("\n");
        status.append("当前回合: ").append(round).append("\n");

        if (state == GameState.SPEAKING || state == GameState.VOTING) {
            status.append("\n👥 *存活玩家* (").append(getAliveCount()).append("/").append(players.size()).append("):\n");
            for (Player p : players.values()) {
                if (p.alive) {
                    // 使用带标识的名字
                    String playerNameWithBadge = StatsService.getPlayerNameWithBadge(p.userId, p.name);
                    status.append("• ").append(playerNameWithBadge);
                    if (state == GameState.SPEAKING) {
                        status.append(BotUtils.isBlank(p.spoke) ? " (未发言)" : " (已发言)");
                    }
                    status.append("\n");
                }
            }
        }

        sendAndRecordMessage(bot, status.toString());
    }

    private void sendPlayerList(AbsSender bot) {
        StringBuilder list = new StringBuilder("👥 *玩家列表*\n\n");

        int aliveCount = 0;
        int undercoverCount = 0;

        for (Player p : players.values()) {
            // 使用带标识的名字
            String playerNameWithBadge = StatsService.getPlayerNameWithBadge(p.userId, p.name);
            list.append("• ").append(playerNameWithBadge);
            if (!p.alive)
                list.append(" (已淘汰)");
            if (p.undercover)
                list.append(" [卧底]");
            list.append("\n");

            if (p.alive)
                aliveCount++;
            if (p.undercover)
                undercoverCount++;
        }

        list.append("\n📊 统计:\n");
        list.append("总玩家: ").append(players.size()).append("\n");
        list.append("存活: ").append(aliveCount).append("\n");
        list.append("卧底: ").append(undercoverCount).append("\n");

        sendAndRecordMessage(bot, list.toString());
    }

    private void sendRules(AbsSender bot) {
        String rules = "📜 *游戏规则*\n\n" +
                "🎯 游戏目标:\n" +
                "• 平民: 找出并投票淘汰所有卧底\n" +
                "• 卧底: 隐藏身份，避免被淘汰\n\n" +
                "🔄 游戏流程:\n" +
                "1. 加入阶段: 玩家点击加入按钮\n" +
                "2. 分配身份: 系统随机分配平民/卧底身份\n" +
                "3. 发言阶段: 玩家描述自己的词语\n" +
                "4. 投票阶段: 投票淘汰可疑玩家\n" +
                "5. 重复3-4直到游戏结束\n\n" +
                "🏆 胜利条件:\n" +
                "• 平民胜利: 淘汰所有卧底\n" +
                "• 卧底胜利: 卧底人数 ≥ 平民人数\n\n" +
                "⏰ 时间限制:\n" +
                "• 发言: " + config.getSpeakingTime() + "秒\n" +
                "• 投票: " + config.getVotingTime() + "秒\n" +
                "• 加入: " + config.getJoinTime() + "秒";

        sendAndRecordMessage(bot, rules);
    }

    private void sendHelp(AbsSender bot) {
        String helpText = "🤖 *谁是卧底游戏机器人*\n\n" +
                "📋 可用命令：\n" +
                "/startgame - 开始新游戏\n" +
                "/players - 查看玩家列表\n" +
                "/status - 查看游戏状态\n" +
                "/rules - 查看游戏规则\n" +
                "/help - 显示此帮助信息\n" +
                "/cancel - 取消当前游戏\n\n" +
                "🎮 游戏流程：\n" +
                "1. 发送 /startgame 开始游戏\n" +
                "2. 点击\"加入游戏\"按钮加入\n" +
                "3. 首次加入需要私聊机器人发送 /start \n" +
                "4. 确认后返回群聊，点击\"开始游戏\"按钮\n" +
                "5. 系统分配身份和词语（私聊）\n" +
                "6. 每轮用 `s描述` 进行发言\n" +
                "7. 投票淘汰疑似卧底的玩家\n" +
                "8. 直到一方胜利\n\n" +
                "⚙️ 配置参数：\n" +
                "最小玩家: " + config.getMinPlayers() + "\n" +
                "最大玩家: " + config.getMaxPlayers() + "\n" +
                "发言时间: " + config.getSpeakingTime() + "秒\n" +
                "投票时间: " + config.getVotingTime() + "秒";

        sendAndRecordMessage(bot, helpText);
    }

    private boolean containsSensitiveWord(String text) {
        if (BotUtils.isBlank(text)) {
            return false;
        }

        String lowerText = text.toLowerCase();
        for (String word : SENSITIVE_WORDS) {
            if (lowerText.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private int getAliveCount() {
        return (int) players.values().stream()
                .filter(p -> p.alive)
                .count();
    }

    /* ================= 定时任务管理 ================= */

    private void scheduleTask(String taskName, Runnable task, long delay, TimeUnit unit) {
        cancelTask(taskName);
        ScheduledFuture<?> future = scheduler.schedule(task, delay, unit);
        scheduledTasks.put(taskName, future);
        GameLogger.logGame(chatId, "安排任务: " + taskName + " 延迟: " + delay + " " + unit);
    }

    private void cancelTask(String taskName) {
        ScheduledFuture<?> future = scheduledTasks.get(taskName);
        if (future != null && !future.isDone()) {
            future.cancel(false);
            GameLogger.logGame(chatId, "取消任务: " + taskName);
        }
        scheduledTasks.remove(taskName);
    }

    private void cancelAllTasks() {
        for (Map.Entry<String, ScheduledFuture<?>> entry : scheduledTasks.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isDone()) {
                entry.getValue().cancel(false);
            }
        }
        scheduledTasks.clear();

        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(false);
            currentTask = null;
        }
    }

    private void cancelCurrentTask() {
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(false);
        }
    }
}