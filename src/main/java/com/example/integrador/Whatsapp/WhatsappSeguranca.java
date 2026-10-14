package com.example.integrador.Whatsapp;

import com.example.integrador.Exception.RegraNegocioException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class WhatsappSeguranca {
    private static final SecureRandom RANDOM = new SecureRandom();
    private WhatsappSeguranca() {}
    public static String numero(String entrada) {
        if (entrada == null) throw new RegraNegocioException("Informe o WhatsApp com código do país");
        String normalizado = entrada.replaceAll("[\\s()\\-]", "");
        if (!normalizado.matches("\\+[1-9][0-9]{7,14}")) {
            throw new RegraNegocioException("Informe o número internacional, por exemplo +5511999999999");
        }
        return normalizado;
    }
    public static String codigo() {
        byte[] bytes = new byte[24]; RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
    public static String hash(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    public static String mascarar(String numero) { return "***" + numero.substring(Math.max(0, numero.length() - 4)); }
}
