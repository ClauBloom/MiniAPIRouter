package com.miniapi.router.core.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 加解密工具类。
 * <p>
 * 基于 AES-256-GCM 算法提供对称加解密能力，用于保护 API Key 等敏感信息。
 * 同时提供密钥脱敏展示方法。
 * </p>
 */
public final class CryptoUtils {

    /** 加密算法：AES/GCM/NoPadding */
    private static final String ALGO = "AES/GCM/NoPadding";

    /** GCM 认证标签长度（比特） */
    private static final int GCM_TAG_BITS = 128;

    /** 初始化向量长度（字节），GCM 推荐 12 字节 */
    private static final int IV_LEN = 12;

    /** 256 位 AES 密钥字节数组 */
    private final byte[] keyBytes;

    /** 安全随机数生成器，用于生成 IV */
    private final SecureRandom random = new SecureRandom();

    /**
     * 使用指定密钥字符串构造加解密工具实例。
     * <p>
     * 将输入的 secret 字符串转换为 32 字节的 AES-256 密钥，
     * 不足 32 字节部分用 0 填充。
     * </p>
     *
     * @param secret 密钥字符串
     */
    public CryptoUtils(String secret) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = i < raw.length ? raw[i] : 0;
        }
        this.keyBytes = key;
    }

    /**
     * AES-256-GCM 加密。
     * <p>
     * 每次加密生成随机 IV，将 IV 拼接在密文前面，
     * 最终输出 Base64 编码字符串。
     * </p>
     *
     * @param plain 明文字符串
     * @return Base64 编码的密文（IV + 密文），若 plain 为 null 则返回 null
     */
    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new RuntimeException("encrypt failed", e);
        }
    }

    /**
     * AES-256-GCM 解密。
     * <p>
     * 从 Base64 编码的输入中分离 IV 和密文，使用 GCM 参数规范进行解密。
     * </p>
     *
     * @param enc Base64 编码的密文（IV + 密文）
     * @return 解密后的明文字符串，若 enc 为 null 则返回 null
     */
    public String decrypt(String enc) {
        if (enc == null) return null;
        try {
            byte[] all = Base64.getDecoder().decode(enc);
            byte[] iv = new byte[IV_LEN];
            byte[] cipherText = new byte[all.length - IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            System.arraycopy(all, IV_LEN, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("decrypt failed", e);
        }
    }

    /**
     * 对 API Key 进行脱敏处理。
     * <p>
     * 保留开头部分和最后 4 位字符，中间用 "..." 替换，
     * 如 "sk-abc1234xyz" -> "sk-a...4xyz"。长度不足 8 位时返回 "****"。
     * </p>
     *
     * @param apiKey 原始 API Key
     * @return 脱敏后的 Key 字符串
     */
    public String mask(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) return "****";
        int len = apiKey.length();
        int prefix = Math.min(4, len / 4);
        return apiKey.substring(0, prefix) + "..." + apiKey.substring(len - 4);
    }
}
