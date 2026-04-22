package com.docgen.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;

/**
 * 密码工具类
 * 使用 PBKDF2WithHmacSHA512 算法进行密码加密和验证
 * 与 Node.js 版本（crypto.pbkdf2Sync）完全兼容
 *
 * 加密参数：
 * - 算法：PBKDF2WithHmacSHA512
 * - 迭代次数：100000
 * - 密钥长度：64 字节（512 位）
 * - 盐值长度：32 字节（256 位）
 * - 存储格式：salt:hash（十六进制编码）
 */
public class PasswordUtil {

    /** 迭代次数（与 Node.js 版本一致） */
    private static final int ITERATIONS = 100000;

    /** 密钥长度（字节），64字节 = 512位（与 Node.js 版本一致） */
    private static final int KEY_LENGTH = 64;

    /** 盐值长度（字节） */
    private static final int SALT_LENGTH = 32;

    /** 加密算法名称 */
    private static final String ALGORITHM = "PBKDF2WithHmacSHA512";

    /** 盐值与哈希的分隔符 */
    private static final String SEPARATOR = ":";

    /** 安全随机数生成器 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 十六进制格式化工具 */
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    /**
     * 私有构造函数，防止实例化
     */
    private PasswordUtil() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 加密密码
     * 生成随机盐值，使用 PBKDF2 算法加密密码
     *
     * @param rawPassword 明文密码
     * @return 加密后的密码字符串，格式：salt:hash（十六进制编码）
     */
    public static String hash(String rawPassword) {
        // 生成随机盐值
        byte[] salt = new byte[SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);

        // 计算密码哈希
        byte[] hash = pbkdf2(rawPassword, salt);

        // 返回 salt:hash 格式（十六进制编码）
        return HEX_FORMAT.formatHex(salt) + SEPARATOR + HEX_FORMAT.formatHex(hash);
    }

    /**
     * 验证密码
     * 将明文密码与存储的加密密码进行比对
     *
     * @param rawPassword    明文密码
     * @param hashedPassword 加密后的密码字符串，格式：salt:hash
     * @return 密码是否匹配
     */
    public static boolean verify(String rawPassword, String hashedPassword) {
        if (rawPassword == null || hashedPassword == null) {
            return false;
        }

        // 解析存储的盐值和哈希
        String[] parts = hashedPassword.split(SEPARATOR);
        if (parts.length != 2) {
            return false;
        }

        try {
            byte[] salt = HEX_FORMAT.parseHex(parts[0]);
            byte[] expectedHash = HEX_FORMAT.parseHex(parts[1]);

            // 使用相同的盐值计算哈希
            byte[] actualHash = pbkdf2(rawPassword, salt);

            // 使用常量时间比较，防止时序攻击
            return slowEquals(expectedHash, actualHash);
        } catch (IllegalArgumentException e) {
            // 十六进制解析失败
            return false;
        }
    }

    /**
     * 使用 PBKDF2 算法计算密码哈希
     *
     * @param password 明文密码
     * @param salt     盐值
     * @return 哈希值
     */
    private static byte[] pbkdf2(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    salt,
                    ITERATIONS,
                    KEY_LENGTH * 8  // 转换为位数
            );
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }

    /**
     * 常量时间比较
     * 防止通过比较时间差异来推断密码内容（时序攻击）
     *
     * @param a 第一个字节数组
     * @param b 第二个字节数组
     * @return 两个数组是否相等
     */
    private static boolean slowEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
