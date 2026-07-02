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

public class SetupWizard {

    public static Path getBaseDir() {
        return Paths.get(System.getProperty("user.home"), ".miniapirouter");
    }

    private static final Path SETUP_FILE = getBaseDir().resolve(".setup-wizard.json");
    private static final Path CRYPTO_SECRET_FILE = getBaseDir().resolve("crypto.secret");
    private static final Path DB_FILE = getBaseDir().resolve("miniapi.db");

    private static final Map<String, String> PROVIDER_DEFAULTS = Map.of(
            "deepseek", "https://api.deepseek.com",
            "openai", "https://api.openai.com",
            "anthropic", "https://api.anthropic.com",
            "azure", "",
            "gemini", "https://generativelanguage.googleapis.com"
    );

    public static void runIfFirstTime(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--skip-setup")) return;
        }

        boolean firstRun = !Files.exists(DB_FILE);
        if (!firstRun) return;

        new SetupWizard().run();
    }

    public static boolean hasSetupData() {
        return Files.exists(SETUP_FILE);
    }

    public static String readSetupData() {
        try {
            return Files.readString(SETUP_FILE);
        } catch (IOException e) {
            return null;
        }
    }

    public static void deleteSetupData() {
        try {
            Files.deleteIfExists(SETUP_FILE);
        } catch (IOException ignored) {
        }
    }

    public static String loadCryptoSecret() {
        try {
            if (Files.exists(CRYPTO_SECRET_FILE)) {
                return Files.readString(CRYPTO_SECRET_FILE).trim();
            }
        } catch (IOException e) {
            System.err.println("  ⚠ 无法读取加密密钥文件: " + e.getMessage());
        }
        return null;
    }

    private static String generateCryptoSecret() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static void saveCryptoSecret(String secret) {
        try {
            Files.createDirectories(CRYPTO_SECRET_FILE.getParent());
            Files.writeString(CRYPTO_SECRET_FILE, secret);
        } catch (IOException e) {
            System.err.println("  ⚠ 无法保存加密密钥: " + e.getMessage());
        }
    }

    private final Scanner scanner = new Scanner(System.in);

    public void run() {
        printBanner();
        System.out.println();
        System.out.println("  检测到首次启动，让我们完成初始配置。");
        System.out.println("  跳过可直接按 Enter 使用默认值（稍后可通过 API 添加）。");
        System.out.println();

        String provider = askProvider();
        String apiKey = askApiKey();
        String baseUrl = askBaseUrl(provider);
        List<String> models = askModels(provider);
        int port = askPort();
        String authToken = askAuthToken();
        String cryptoSecret = askCryptoSecret();

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

        if (!confirm()) {
            System.out.println("  已跳过配置。服务将使用默认设置启动。");
            System.out.println("  你可以稍后通过 API 添加 API Key。");
            System.out.println();
            return;
        }

        saveSetupData(provider, apiKey, baseUrl, models, port, authToken);
        saveCryptoSecret(cryptoSecret);

        System.setProperty("server.port", String.valueOf(port));
        System.setProperty("miniapi.router.auth-token", authToken);
        System.setProperty("miniapi.router.crypto-secret", cryptoSecret);

        System.out.println("  ✓ 配置已保存，正在启动服务...");
        System.out.println();
    }

    private void printBanner() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════╗");
        System.out.println("  ║         MiniAPIRouter Standalone          ║");
        System.out.println("  ║         初 次 启 动 引 导                  ║");
        System.out.println("  ╚══════════════════════════════════════════╝");
    }

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

    private String askBaseUrl(String provider) {
        System.out.println();
        System.out.println("  ── 步骤 3/5: 输入 Base URL ──");
        String defaultUrl = PROVIDER_DEFAULTS.getOrDefault(provider, "");
        if (!defaultUrl.isEmpty()) {
            System.out.print("  Base URL (回车使用 " + defaultUrl + "): ");
            String input = scanner.nextLine().trim();
            return input.isEmpty() ? defaultUrl : input;
        }
        while (true) {
            System.out.print("  Base URL: ");
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) return input;
            System.out.println("  ⚠ Base URL 不能为空");
        }
    }

    private List<String> askModels(String provider) {
        System.out.println();
        System.out.println("  ── 步骤 4/5: 输入支持的模型 ──");
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
        List<String> models = new ArrayList<>();
        for (String m : input.split(",")) {
            String trimmed = m.trim();
            if (!trimmed.isEmpty()) models.add(trimmed);
        }
        return models.isEmpty() ? List.of(suggested) : models;
    }

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

    private String askAuthToken() {
        System.out.println();
        System.out.print("  认证 Token (回车使用 sk-miniapi-standalone): ");
        String input = scanner.nextLine().trim();
        return input.isEmpty() ? "sk-miniapi-standalone" : input;
    }

    private String askCryptoSecret() {
        System.out.println();
        System.out.println("  ── 额外: API Key 加密密钥 ──");
        String generated = generateCryptoSecret();
        System.out.print("  加密密钥 (回车自动生成 32 字节 Base64 密钥): ");
        String input = scanner.nextLine().trim();
        return input.isEmpty() ? generated : input;
    }

    private boolean confirm() {
        System.out.print("  确认并启动? [Y/n]: ");
        String input = scanner.nextLine().trim().toLowerCase();
        return input.isEmpty() || input.equals("y") || input.equals("yes");
    }

    private void saveSetupData(String provider, String apiKey, String baseUrl,
                                List<String> models, int port, String authToken) {
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

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 8) return "***";
        return key.substring(0, 3) + "..." + key.substring(key.length() - 4);
    }
}
