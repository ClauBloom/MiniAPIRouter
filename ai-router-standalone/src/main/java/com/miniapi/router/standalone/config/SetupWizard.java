package com.miniapi.router.standalone.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * 首次启动配置向导。
 * <p>
 * 在应用首次启动时（数据库文件不存在时），通过交互式命令行引导用户完成初始配置：
 * 1. 选择 AI 供应商
 * 2. 输入 API Key
 * 3. 配置 Base URL
 * 4. 选择支持的模型
 * 5. 设置服务端口和认证 Token
 * 6. 生成或设置加密密钥
 * </p>
 * 配置完成后将数据保存到临时文件，由 DataInitializer 读取并创建 API Key。
 * 加密密钥持久化到 standalone.yaml 文件中。
 */
public class SetupWizard {

    /**
     * 获取基础数据目录路径（用户主目录下的 .miniapirouter）。
     *
     * @return 基础数据目录路径
     */
    public static Path getBaseDir() {
        return Paths.get(System.getProperty("user.home"), ".miniapirouter");
    }

    private static final Path SETUP_FILE = getBaseDir().resolve(".setup-wizard.json"); // 临时配置文件路径
    private static final Path STANDALONE_YAML = getBaseDir().resolve("standalone.yaml"); // 持久化配置文件路径
    private static final Path DB_FILE = getBaseDir().resolve("miniapi.db"); // SQLite 数据库文件路径

    // 各供应商的默认 Base URL
    private static final Map<String, String> PROVIDER_DEFAULTS = Map.of(
            "deepseek", "https://api.deepseek.com",
            "openai", "https://api.openai.com",
            "anthropic", "https://api.anthropic.com",
            "azure", "",
            "gemini", "https://generativelanguage.googleapis.com"
    );

    /**
     * 如果是首次启动（数据库文件不存在），则运行配置向导。
     * 可通过命令行参数 --skip-setup 跳过。
     *
     * @param args 命令行参数
     */
    public static void runIfFirstTime(String[] args) {
        // 检查是否跳过配置向导
        for (String arg : args) {
            if (arg.startsWith("--skip-setup")) return;
        }

        // 判断是否首次启动（数据库文件不存在）
        boolean firstRun = !Files.exists(DB_FILE);
        if (!firstRun) return;

        new SetupWizard().run();
    }

    /**
     * 检查是否存在向导配置数据（.setup-wizard.json 文件）。
     *
     * @return 如果配置文件存在则返回 true
     */
    public static boolean hasSetupData() {
        return Files.exists(SETUP_FILE);
    }

    /**
     * 读取向导配置数据。
     *
     * @return 配置数据的 JSON 字符串，读取失败返回 null
     */
    public static String readSetupData() {
        try {
            return Files.readString(SETUP_FILE);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 删除向导配置数据文件（使用后清理）。
     */
    public static void deleteSetupData() {
        try {
            Files.deleteIfExists(SETUP_FILE);
        } catch (IOException ignored) {
        }
    }

    /**
     * 从 standalone.yaml 文件加载加密密钥。
     *
     * @return 加密密钥字符串，如果不存在或读取失败则返回 null
     */
    public static String loadCryptoSecret() {
        try {
            if (Files.exists(STANDALONE_YAML)) {
                for (String line : Files.readAllLines(STANDALONE_YAML)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("crypto-secret:")) {
                        String secret = trimmed.substring("crypto-secret:".length()).trim();
                        if (!secret.isEmpty()) {
                            return secret;
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("  ⚠ 无法读取 " + STANDALONE_YAML + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * 确保加密密钥存在。
     * 先尝试从配置文件加载，如果不存在则生成新密钥并保存。
     * 如果数据库已存在但密钥不存在，则报错退出（无法解密已有数据）。
     *
     * @return 加密密钥字符串
     */
    public static String ensureCryptoSecret() {
        String secret = loadCryptoSecret();
        if (secret != null && !secret.isEmpty()) {
            return secret;
        }
        // 数据库已存在但没有加密密钥，无法解密已有数据
        if (Files.exists(DB_FILE)) {
            System.err.println("  ⚠ 数据库已存在但未找到 crypto-secret，请检查 " + STANDALONE_YAML);
            System.exit(1);
        }
        // 首次启动，生成新密钥并保存
        secret = generateCryptoSecret();
        saveCryptoSecret(secret);
        return secret;
    }

    /**
     * 生成 32 字节的随机加密密钥（Base64 编码）。
     *
     * @return Base64 编码的加密密钥
     */
    private static String generateCryptoSecret() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /**
     * 将加密密钥保存到 standalone.yaml 文件。
     *
     * @param secret 加密密钥
     */
    private static void saveCryptoSecret(String secret) {
        try {
            Files.createDirectories(STANDALONE_YAML.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("# MiniAPIRouter Standalone 配置文件\n");
            sb.append("crypto-secret: ").append(secret).append("\n");
            Files.writeString(STANDALONE_YAML, sb.toString());
        } catch (IOException e) {
            System.err.println("  ⚠ 无法保存加密密钥: " + e.getMessage());
        }
    }

    private final Scanner scanner = new Scanner(System.in); // 控制台输入扫描器

    /**
     * 运行交互式配置向导。
     * 依次引导用户完成供应商选择、API Key 输入、Base URL 配置、模型选择、端口和 Token 设置。
     */
    public void run() {
        printBanner();
        System.out.println();
        System.out.println("  检测到首次启动，让我们完成初始配置。");
        System.out.println("  跳过可直接按 Enter 使用默认值（稍后可通过 API 添加）。");
        System.out.println();

        String provider = askProvider();     // 步骤1：选择供应商
        String apiKey = askApiKey();         // 步骤2：输入 API Key
        String baseUrl = askBaseUrl(provider); // 步骤3：配置 Base URL
        List<String> models = askModels(provider); // 步骤4：选择模型
        int port = askPort();                // 步骤5：设置端口
        String authToken = askAuthToken();   // 设置认证 Token
        String cryptoSecret = askCryptoSecret(); // 设置加密密钥

        // 打印配置摘要
        System.out.println();
        System.out.println("  ┌──────────────────────────────────────────┐");
        System.out.println("  │              配置摘要                      │");
        System.out.println("  ├──────────────────────────────────────────┤");
        System.out.printf( "  │  供应商     : %-28s│%n", provider);
        System.out.printf( "  │  API Key    : %-28s│%n", maskKey(apiKey));
        System.out.printf( "  │  Base URL   : %-28s│%n", baseUrl);
        System.out.printf( "  │  模型       : %-28s│%n", String.join(", ", models));
        System.out.printf( "  │  端口       : %-28d│%n", port);
        System.out.printf( "  │  认证 Token : %-28s│%n", authToken);
        System.out.println("  └──────────────────────────────────────────┘");
        System.out.println();

        // 确认配置
        if (!confirm()) {
            System.out.println("  已跳过配置。服务将使用默认设置启动。");
            System.out.println("  你可以稍后通过 API 添加 API Key。");
            System.out.println();
            return;
        }

        // 保存配置数据
        saveSetupData(provider, apiKey, baseUrl, models, port, authToken);
        saveCryptoSecret(cryptoSecret);

        // 设置系统属性
        System.setProperty("server.port", String.valueOf(port));
        System.setProperty("miniapi.router.auth-token", authToken);
        System.setProperty("miniapi.router.crypto-secret", cryptoSecret);

        System.out.println("  ✓ 配置已保存，正在启动服务...");
        System.out.println();
    }

    /**
     * 打印配置向导横幅。
     */
    private void printBanner() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════╗");
        System.out.println("  ║         MiniAPIRouter Standalone          ║");
        System.out.println("  ║         初 次 启 动 引 导                  ║");
        System.out.println("  ╚══════════════════════════════════════════╝");
    }

    /**
     * 步骤 1：选择 AI 供应商。
     *
     * @return 供应商名称
     */
    private String askProvider() {
        System.out.println("  ── 步骤 1/5: 选择 AI 供应商 ──");
        System.out.println("    1) deepseek   (推荐, 支持 deepseek-v4-flash/pro)");
        System.out.println("    2) openai     (GPT-4o, GPT-4o-mini, ...)");
        System.out.println("    3) anthropic  (Claude Sonnet/Opus/Haiku)");
        System.out.println("    4) azure      (Azure OpenAI)");
        System.out.println("    5) gemini     (Google Gemini)");
        System.out.print("  选择 [1-5] (默认 1): ");
        String input = scanner.nextLine().trim();
        return switch (input) {
            case "2" -> "openai";
            case "3" -> "anthropic";
            case "4" -> "azure";
            case "5" -> "gemini";
            default -> "deepseek";
        };
    }

    /**
     * 步骤 2：输入 API Key（不能为空）。
     *
     * @return API Key 字符串
     */
    private String askApiKey() {
        System.out.println();
        System.out.println("  ── 步骤 2/5: 输入 API Key ──");
        while (true) {
            System.out.print("  API Key (sk-xxx): ");
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) return input;
            System.out.println("  ⚠ API Key 不能为空");
        }
    }

    /**
     * 步骤 3：输入 Base URL。
     * 如果供应商有默认 URL，则可直接使用默认值。
     *
     * @param provider 供应商名称
     * @return Base URL
     */
    private String askBaseUrl(String provider) {
        System.out.println();
        System.out.println("  ── 步骤 3/5: 输入 Base URL ──");
        String defaultUrl = PROVIDER_DEFAULTS.getOrDefault(provider, "");
        if (!defaultUrl.isEmpty()) {
            System.out.print("  Base URL (回车使用 " + defaultUrl + "): ");
            String input = scanner.nextLine().trim();
            return input.isEmpty() ? defaultUrl : input;
        }
        // 没有默认 URL，必须手动输入
        while (true) {
            System.out.print("  Base URL: ");
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) return input;
            System.out.println("  ⚠ Base URL 不能为空");
        }
    }

    /**
     * 步骤 4：输入支持的模型列表。
     * 根据供应商提供推荐模型，用户可自定义输入。
     *
     * @param provider 供应商名称
     * @return 模型名称列表
     */
    private List<String> askModels(String provider) {
        System.out.println();
        System.out.println("  ── 步骤 4/5: 输入支持的模型 ──");
        // 根据供应商推荐默认模型
        String suggested = switch (provider) {
            case "deepseek" -> "deepseek-v4-flash";
            case "openai" -> "gpt-4o-mini";
            case "anthropic" -> "claude-sonnet-4-20250514";
            case "azure" -> "gpt-4o";
            case "gemini" -> "gemini-2.0-flash";
            default -> "";
        };
        System.out.print("  模型列表 (逗号分隔, 回车使用 " + suggested + "): ");
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            return List.of(suggested);
        }
        // 解析逗号分隔的模型列表
        List<String> models = new ArrayList<>();
        for (String m : input.split(",")) {
            String trimmed = m.trim();
            if (!trimmed.isEmpty()) models.add(trimmed);
        }
        return models.isEmpty() ? List.of(suggested) : models;
    }

    /**
     * 步骤 5：设置服务端口（默认 9090）。
     *
     * @return 端口号
     */
    private int askPort() {
        System.out.println();
        System.out.println("  ── 步骤 5/5: 服务端口 ──");
        System.out.print("  端口 (默认 9090): ");
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) return 9090;
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            System.out.println("  ⚠ 无效端口, 使用默认 9090");
            return 9090;
        }
    }

    /**
     * 设置认证 Token（默认 sk-miniapi-standalone）。
     *
     * @return 认证 Token
     */
    private String askAuthToken() {
        System.out.println();
        System.out.print("  认证 Token (回车使用 sk-miniapi-standalone): ");
        String input = scanner.nextLine().trim();
        return input.isEmpty() ? "sk-miniapi-standalone" : input;
    }

    /**
     * 设置 API Key 加密密钥。
     * 可自动生成 32 字节 Base64 密钥，或由用户手动输入。
     *
     * @return 加密密钥
     */
    private String askCryptoSecret() {
        System.out.println();
        System.out.println("  ── 额外: API Key 加密密钥 ──");
        String generated = generateCryptoSecret();
        System.out.print("  加密密钥 (回车自动生成 32 字节 Base64 密钥): ");
        String input = scanner.nextLine().trim();
        return input.isEmpty() ? generated : input;
    }

    /**
     * 确认配置并启动。
     *
     * @return true 表示确认，false 表示跳过
     */
    private boolean confirm() {
        System.out.print("  确认并启动? [Y/n]: ");
        String input = scanner.nextLine().trim().toLowerCase();
        return input.isEmpty() || input.equals("y") || input.equals("yes");
    }

    /**
     * 将向导配置数据保存为 JSON 文件。
     *
     * @param provider  供应商
     * @param apiKey    API Key
     * @param baseUrl   Base URL
     * @param models    模型列表
     * @param port      端口
     * @param authToken 认证 Token
     */
    private void saveSetupData(String provider, String apiKey, String baseUrl,
                                List<String> models, int port, String authToken) {
        // 手动拼接 JSON 字符串
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"provider\":\"").append(escape(provider)).append("\",");
        sb.append("\"api_key\":\"").append(escape(apiKey)).append("\",");
        sb.append("\"base_url\":\"").append(escape(baseUrl)).append("\",");
        sb.append("\"models\":[");
        for (int i = 0; i < models.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escape(models.get(i))).append("\"");
        }
        sb.append("],");
        sb.append("\"port\":").append(port).append(",");
        sb.append("\"auth_token\":\"").append(escape(authToken)).append("\"");
        sb.append("}");
        try {
            Files.createDirectories(SETUP_FILE.getParent());
            Files.writeString(SETUP_FILE, sb.toString());
        } catch (IOException e) {
            System.err.println("  ⚠ 无法保存配置: " + e.getMessage());
        }
    }

    /**
     * JSON 字符串转义（转义反斜杠和双引号）。
     *
     * @param s 原始字符串
     * @return 转义后的字符串
     */
    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 对 API Key 进行掩码处理（仅显示前 3 位和后 4 位）。
     *
     * @param key 原始 API Key
     * @return 掩码后的字符串
     */
    private String maskKey(String key) {
        if (key == null || key.length() < 8) return "***";
        return key.substring(0, 3) + "..." + key.substring(key.length() - 4);
    }
}
