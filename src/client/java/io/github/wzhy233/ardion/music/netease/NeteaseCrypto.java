package io.github.wzhy233.ardion.music.netease;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public final class NeteaseCrypto {
    private static final String PRESET_KEY = "0CoJUm6Qyw8W8jud";
    private static final String IV = "0102030405060708";
    private static final String RSA_PUB_KEY = "010001";
    private static final String RSA_MODULUS =
            "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725"
                    + "152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312"
                    + "ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424"
                    + "d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7";
    private static final char[] BASE62 =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    private NeteaseCrypto() {
    }

    public static Map<String, String> weapi(String jsonText) throws Exception {
        String secret = randomString(16);
        String first = aesEncrypt(jsonText, PRESET_KEY);
        String params = aesEncrypt(first, secret);
        String encSecKey = rsaEncrypt(secret);
        Map<String, String> out = new HashMap<>();
        out.put("params", params);
        out.put("encSecKey", encSecKey);
        return out;
    }

    private static String aesEncrypt(String data, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8)));
        return java.util.Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static String rsaEncrypt(String secret) {
        String reversed = new StringBuilder(secret).reverse().toString();
        BigInteger text = new BigInteger(1, reversed.getBytes(StandardCharsets.UTF_8));
        BigInteger pubKey = new BigInteger(RSA_PUB_KEY, 16);
        BigInteger modulus = new BigInteger(RSA_MODULUS, 16);
        String hex = text.modPow(pubKey, modulus).toString(16);
        return "0".repeat(Math.max(0, 256 - hex.length())) + hex;
    }

    private static String randomString(int len) {
        char[] out = new char[len];
        for (int i = 0; i < len; i++) out[i] = BASE62[RNG.nextInt(BASE62.length)];
        return new String(out);
    }
}
