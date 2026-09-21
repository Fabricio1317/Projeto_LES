package com.games.cliente.application.validation;

/**
 * Validação de CPF por dígito verificador.
 * Lógica testada isoladamente antes da integração (ver relatório de testes unitários).
 */
public final class CpfValidator {

    private CpfValidator() {}

    public static boolean isValid(String cpfRaw) {
        if (cpfRaw == null) return false;
        String cpf = cpfRaw.replaceAll("\\D", "");
        if (cpf.length() != 11) return false;
        if (cpf.chars().distinct().count() == 1) return false;

        int[] d = new int[11];
        for (int i = 0; i < 11; i++) d[i] = cpf.charAt(i) - '0';

        int soma1 = 0;
        for (int i = 0; i < 9; i++) soma1 += d[i] * (10 - i);
        int resto1 = soma1 % 11;
        int dv1 = (resto1 < 2) ? 0 : 11 - resto1;
        if (dv1 != d[9]) return false;

        int soma2 = 0;
        for (int i = 0; i < 10; i++) soma2 += d[i] * (11 - i);
        int resto2 = soma2 % 11;
        int dv2 = (resto2 < 2) ? 0 : 11 - resto2;
        return dv2 == d[10];
    }

    /** Remove formatação, mantendo apenas dígitos. */
    public static String somenteDigitos(String cpf) {
        return cpf == null ? null : cpf.replaceAll("\\D", "");
    }
}
