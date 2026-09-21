package com.games.cliente.application.validation;

import java.util.regex.Pattern;

/**
 * RNF0031 — senha forte: mínimo de 8 caracteres, com letra maiúscula,
 * minúscula e caractere especial.
 */
public final class SenhaValidator {

    private static final Pattern MAIUSCULA = Pattern.compile("[A-Z]");
    private static final Pattern MINUSCULA = Pattern.compile("[a-z]");
    private static final Pattern ESPECIAL = Pattern.compile("[^A-Za-z0-9]");

    private SenhaValidator() {}

    public static boolean isForte(String senha) {
        if (senha == null || senha.length() < 8) return false;
        return MAIUSCULA.matcher(senha).find()
                && MINUSCULA.matcher(senha).find()
                && ESPECIAL.matcher(senha).find();
    }
}
