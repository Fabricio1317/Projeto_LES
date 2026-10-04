package com.games.vendas.application;

import com.games.cliente.application.exception.RegraNegocioException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * RF0034 — frete calculado a partir dos itens (peso total) e do endereço de
 * entrega (UF): valor base por região + R$ 5,00 por kg.
 * SP: R$ 10,00 · demais estados do Sudeste e Sul: R$ 15,00 · demais: R$ 25,00.
 */
@Service
public class FreteService {

    private static final BigDecimal VALOR_POR_KG = new BigDecimal("5.00");
    private static final Set<String> SUL_SUDESTE = Set.of("RJ", "MG", "ES", "PR", "SC", "RS");
    private static final Set<String> UFS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA",
            "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    public BigDecimal calcular(BigDecimal pesoTotalKg, String estado) {
        String uf = estado == null ? "" : estado.trim().toUpperCase();
        if (!UFS.contains(uf)) {
            throw new RegraNegocioException("RF0034", "Informe uma UF válida no endereço de entrega para calcular o frete.");
        }
        BigDecimal base = "SP".equals(uf) ? new BigDecimal("10.00")
                : SUL_SUDESTE.contains(uf) ? new BigDecimal("15.00")
                : new BigDecimal("25.00");
        return base.add(VALOR_POR_KG.multiply(pesoTotalKg)).setScale(2, RoundingMode.HALF_UP);
    }
}
