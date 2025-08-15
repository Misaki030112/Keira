package com.hinadt.ai;

import com.hinadt.ai.prompt.PromptComposer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 使用与生产一致的 PromptComposer 组装 system 提示词，真实 DeepSeek 调用，测量耗时。
 * - 将 API_KEY 常量改为你的真实 DeepSeek 密钥，或设置环境变量 DEEPSEEK_API_KEY。
 */
public class RealDeepSeekE2ELatencyTest {

    private static final String TEST_API_KEY = "sk-ebd9611424c34a5e9aab24b258f1bb8d";

    private static String resolveApiKey() {
        String env = System.getenv("DEEPSEEK_API_KEY");
        if (env != null && !env.isBlank()) return env;
        return TEST_API_KEY;
    }

    private static ChatModel buildDeepSeekModel(String apiKey) {
        var api = DeepSeekApi.builder().apiKey(apiKey).build();
        var options = DeepSeekChatOptions.builder()
                .model("deepseek-chat")
                .temperature(0.7)
                .maxTokens(800)
                .build();
        return DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .defaultOptions(options)
                .build();
    }

    @Test
    void deepseek_chat_with_real_promptcomposer() {
        String apiKey = resolveApiKey();
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank() && !apiKey.contains("PUT_YOUR_"),
                "未提供 DeepSeek API Key。请设置环境变量 DEEPSEEK_API_KEY 或修改 TEST_API_KEY 常量。");

        // 准备真实的 system 提示词（与生产一致的 PromptComposer）
        PromptComposer composer = new PromptComposer();

        String playerName = "TestPlayer";
        // 真实业务中的上下文（这里硬编码更接近线上体量，避免过短）
        String detailedContext = "位置:(123,65,-321) 主世界\n" +
                "生命值: 18/20, 饥饿: 16/20, 经验: 27\n" +
                "所处生物群系: plains, 天气: 晴朗, 时间: 傍晚\n" +
                "附近方块: 草方块, 橡木, 石头, 沙砾, 煤矿\n" +
                "效果: 夜视(60s), 抗性(30s)";
        String conversationContext = "[18:21:01] 玩家: 给我来一把钻石剑\n" +
                "[18:21:03] AI: 我可以为你合成或给予，需要确认权限。\n" +
                "[18:21:09] 玩家: 我是管理员。\n" +
                "[18:21:12] AI: 已验证管理员权限，可执行物品给予。";
        String permissionContext = "玩家为普通用户，危险操作需要权限验证。";
        boolean isAdmin = false;
        String memoryContext = "**玩家记忆信息**:\n" +
                "- 家: overworld (256, 72, -180), 描述: 基岩防爆房\n" +
                "- 矿洞入口: overworld (120, 54, -90), 描述: 丰富煤铁\n" +
                "- 海底神殿: overworld (1024, 63, 980), 描述: 海晶碎片\n" +
                "- 末地门: overworld (-320, 28, 640), 描述: 要小心掉落\n";
        String serverContext = "**服务器状态**:\n" +
                "- 在线玩家数: 5\n" +
                "- 在线玩家: Alice Bob Carol Dave TestPlayer\n" +
                "- 服务器类型: 专用服务器\n";
        String toolAvailability = "- 物品、传送、天气、世界分析、玩家信息：可用\n- 管理员工具：受限，危险操作需权限验证，将被拒绝\n";

        String systemPrompt = composer.composeSystemPrompt(
                playerName,
                detailedContext,
                conversationContext,
                permissionContext,
                isAdmin,
                memoryContext,
                serverContext,
                toolAvailability);

        ChatModel model = buildDeepSeekModel(apiKey);
        ChatClient client = ChatClient.builder(model).build();

        // 重复请求以观察波动
        int runs = 3;
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE, sum = 0;
        String lastContent = null;
        for (int i = 0; i < runs; i++) {
            long start = System.currentTimeMillis();
            String content = client
                    .prompt()
                    .system(systemPrompt)
                    .user("你好你在干嘛")
                    .call()
                    .content();
            long cost = System.currentTimeMillis() - start;
            min = Math.min(min, cost);
            max = Math.max(max, cost);
            sum += cost;
            lastContent = content;
        }
        long avg = sum / runs;

        System.out.println("[DeepSeek/E2E] minMs=" + min + ", maxMs=" + max + ", avgMs=" + avg +
                "\n--- systemPrompt ---\n" + systemPrompt +
                "\n--- last content ---\n" + lastContent);

        assertNotNull(lastContent);
        assertFalse(lastContent.isBlank());
    }
}
