package bot.wodibot.model;

public class Player {
    public long userId;
    public String name;

    public boolean undercover;
    public boolean alive;
    public String spoke;
    public boolean voted;

    public String word;

    public Player(long userId, String name) {
        this.userId = userId;
        this.name = name;
    }
}