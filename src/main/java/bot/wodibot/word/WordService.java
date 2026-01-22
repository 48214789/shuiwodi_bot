package bot.wodibot.word;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WordService {
    
    public static class WordPair {
        public final List<String> civilians; // 平民词列表
        public final String undercover;      // 卧底词
        
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
        POOL.clear();
        
        try (InputStream in = WordService.class
                .getClassLoader()
                .getResourceAsStream("words.txt")) {
            
            if (in == null) {
                System.err.println("❌ 找不到 words.txt 文件");
                loadDefaultWords();
                return;
            }
            
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                
                // 跳过空行和注释
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // 支持格式：平民词,卧底词
                String[] parts = line.split(",");
                if (parts.length != 2) {
                    System.err.println("❌ 格式错误: " + line);
                    continue;
                }
                
                String civilianWord = parts[0].trim();
                String undercoverWord = parts[1].trim();
                
                if (civilianWord.isEmpty() || undercoverWord.isEmpty()) {
                    System.err.println("❌ 词语为空: " + line);
                    continue;
                }
                
                POOL.add(new WordPair(
                        Collections.singletonList(civilianWord),
                        undercoverWord
                ));
            }
            
            if (POOL.isEmpty()) {
                System.err.println("⚠️ 词库为空，加载默认词库");
                loadDefaultWords();
            } else {
                System.out.println("✅ 加载 " + POOL.size() + " 个词对");
            }
            
        } catch (Exception e) {
            System.err.println("❌ 加载词库失败: " + e.getMessage());
            loadDefaultWords();
        }
    }
    
    // 新增：重新加载并返回词对数量
    public static synchronized int reloadAndGetCount() {
        reload();
        return POOL.size();
    }
    
    private static void loadDefaultWords() {
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
    
    public static WordPair randomForChat(long chatId) {
        if (POOL.isEmpty()) {
            reload();
        }
        
        if (POOL.isEmpty()) {
            return null;
        }
        
        return POOL.get(RANDOM.nextInt(POOL.size()));
    }
    
    public static int getWordCount() {
        return POOL.size();
    }
}