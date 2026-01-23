package bot.wodibot.word;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WordService {
    
    public static class WordPair {
        public final List<String> civilians;
        public final String undercover;
        
        public WordPair(List<String> civilians, String undercover) {
            this.civilians = civilians;
            this.undercover = undercover;
        }
    }
    
    private static final List<WordPair> POOL = new ArrayList<>();
    private static final Random RANDOM = new Random();
    
    static {
        reload();
    }
    
    public static synchronized void reload() {
        System.out.println("📚 开始加载词库...");
        POOL.clear();
        
        boolean loaded = false;
        
        // 方法1：尝试从文件系统加载
        try {
            File[] possiblePaths = {
                new File("src/main/resources/words.txt"),
                new File("words.txt"),
                new File("./words.txt"),
                new File("config/words.txt")
            };
            
            for (File wordFile : possiblePaths) {
                if (wordFile.exists() && wordFile.isFile()) {
                    System.out.println("✅ 找到词库文件: " + wordFile.getAbsolutePath());
                    loadFromFile(wordFile);
                    loaded = true;
                    break;
                } else {
                    System.out.println("  检查: " + wordFile.getAbsolutePath() + " - 不存在");
                }
            }
        } catch (Exception e) {
            System.err.println("❌ 从文件加载词库失败: " + e.getMessage());
        }
        
        // 方法2：如果文件系统没找到，尝试类路径加载
        if (!loaded) {
            System.out.println("🔍 尝试从类路径加载词库...");
            try {
                InputStream in = WordService.class.getClassLoader()
                        .getResourceAsStream("words.txt");
                if (in != null) {
                    System.out.println("✅ 从类路径找到词库文件");
                    loadFromInputStream(in);
                    loaded = true;
                } else {
                    System.err.println("❌ 类路径也没有词库文件");
                }
            } catch (Exception e) {
                System.err.println("❌ 从类路径加载词库失败: " + e.getMessage());
            }
        }
        
        // 方法3：如果都没找到，使用默认词库
        if (!loaded || POOL.isEmpty()) {
            System.err.println("⚠️ 词库加载失败，使用默认词库");
            loadDefaultWords();
        }
        
        System.out.println("✅ 词库加载完成，共 " + POOL.size() + " 个词对");
    }
    
    private static void loadFromFile(File wordFile) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(wordFile), StandardCharsets.UTF_8))) {
            
            int lineCount = 0;
            String line;
            while ((line = br.readLine()) != null) {
                lineCount++;
                line = line.trim();
                
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                String[] parts = line.split(",");
                if (parts.length != 2) {
                    System.err.println("❌ 第" + lineCount + "行格式错误: " + line);
                    continue;
                }
                
                String civilianWord = parts[0].trim();
                String undercoverWord = parts[1].trim();
                
                if (civilianWord.isEmpty() || undercoverWord.isEmpty()) {
                    System.err.println("❌ 第" + lineCount + "行词语为空: " + line);
                    continue;
                }
                
                POOL.add(new WordPair(
                        Collections.singletonList(civilianWord),
                        undercoverWord
                ));
            }
            
            System.out.println("  读取 " + lineCount + " 行，有效词对 " + POOL.size() + " 个");
        }
    }
    
    private static void loadFromInputStream(InputStream in) throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        
        int lineCount = 0;
        String line;
        while ((line = br.readLine()) != null) {
            lineCount++;
            line = line.trim();
            
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            String[] parts = line.split(",");
            if (parts.length != 2) {
                System.err.println("❌ 第" + lineCount + "行格式错误: " + line);
                continue;
            }
            
            String civilianWord = parts[0].trim();
            String undercoverWord = parts[1].trim();
            
            if (civilianWord.isEmpty() || undercoverWord.isEmpty()) {
                System.err.println("❌ 第" + lineCount + "行词语为空: " + line);
                continue;
            }
            
            POOL.add(new WordPair(
                    Collections.singletonList(civilianWord),
                    undercoverWord
            ));
        }
        
        System.out.println("  从类路径读取 " + lineCount + " 行，有效词对 " + POOL.size() + " 个");
    }
    
    private static void loadDefaultWords() {
        System.out.println("📝 加载默认词库...");
        POOL.clear();
        POOL.add(new WordPair(Arrays.asList("苹果", "Apple"), "梨"));
        POOL.add(new WordPair(Arrays.asList("牛奶", "Milk"), "豆浆"));
        POOL.add(new WordPair(Arrays.asList("飞机", "Airplane"), "火车"));
        POOL.add(new WordPair(Arrays.asList("微信", "WeChat"), "QQ"));
        POOL.add(new WordPair(Arrays.asList("篮球", "Basketball"), "足球"));
        POOL.add(new WordPair(Arrays.asList("夏天", "Summer"), "冬天"));
        POOL.add(new WordPair(Arrays.asList("咖啡", "Coffee"), "茶"));
        POOL.add(new WordPair(Arrays.asList("猫", "Cat"), "狗"));
        POOL.add(new WordPair(Arrays.asList("月亮", "Moon"), "太阳"));
        POOL.add(new WordPair(Arrays.asList("红色", "Red"), "蓝色"));
        System.out.println("✅ 加载默认词库 " + POOL.size() + " 个词对");
    }
    
    // 新增：重新加载并返回词对数量
    public static synchronized int reloadAndGetCount() {
        reload();
        return POOL.size();
    }
    
    public static WordPair randomForChat(long chatId) {
        if (POOL.isEmpty()) {
            System.err.println("⚠️ 词库为空，重新加载");
            reload();
        }
        
        if (POOL.isEmpty()) {
            System.err.println("❌ 词库加载失败，返回null");
            return null;
        }
        
        WordPair pair = POOL.get(RANDOM.nextInt(POOL.size()));
        System.out.println("🎲 为聊天 " + chatId + " 选择词对: " + 
                         pair.civilians.get(0) + " / " + pair.undercover);
        return pair;
    }
    
    public static int getWordCount() {
        return POOL.size();
    }
}