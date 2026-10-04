package com.games.vendas.application;

import com.games.cliente.adapter.out.persistence.ClienteRepository;
import com.games.cliente.application.AuditoriaService;
import com.games.cliente.application.exception.ClienteNaoEncontradoException;
import com.games.cliente.application.exception.RegraNegocioException;
import com.games.vendas.adapter.in.web.dto.CupomRequest;
import com.games.vendas.adapter.out.persistence.CupomRepository;
import com.games.vendas.domain.entity.Cupom;
import com.games.vendas.domain.enums.TipoCupom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cupons usados como forma de pagamento (RF0036 / RF0037).
 * RN0033 — no máximo um cupom promocional por compra.
 * RN0036 — gera o cupom de troca com a diferença quando os cupons superam a compra.
 * RN0037 — valida a validade e a veracidade dos cupons.
 */
@Service
public class CupomService {

    private final CupomRepository repository;
    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoria;

    public CupomService(CupomRepository repository, ClienteRepository clienteRepository, AuditoriaService auditoria) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.auditoria = auditoria;
    }

    @Transactional
    public Cupom cadastrar(CupomRequest req) {
        if (repository.existsByCodigoIgnoreCase(req.codigo())) {
            throw new RegraNegocioException("CUPOM_DUPLICADO", "Já existe um cupom com o código " + req.codigo() + ".");
        }
        if (req.tipo() == TipoCupom.TROCA) {
            if (req.clienteId() == null) {
                throw new RegraNegocioException("CUPOM_TROCA_SEM_CLIENTE", "Cupom de troca deve pertencer a um cliente.");
            }
            if (!clienteRepository.existsById(req.clienteId())) {
                throw new ClienteNaoEncontradoException(req.clienteId());
            }
        }
        Long clienteId = req.tipo() == TipoCupom.TROCA ? req.clienteId() : null;
        Cupom cupom = repository.save(new Cupom(req.codigo().toUpperCase(), req.tipo(), req.valor(),
                clienteId, req.validade(), null));
        auditoria.registrar("Cupom", cupom.getId(), "INSERT", "admin",
                "Cupom " + cupom.getTipo() + " " + cupom.getCodigo() + " de R$ " + cupom.getValor());
        return cupom;
    }

    @Transactional(readOnly = true)
    public List<Cupom> listarTodos() {
        return repository.findAllByOrderByIdDesc();
    }

    /** Cupons de troca do cliente que ainda podem ser usados no pagamento. */
    @Transactional(readOnly = true)
    public List<Cupom> listarTrocaDisponiveis(Long clienteId) {
        LocalDate hoje = LocalDate.now();
        return repository.findByClienteIdAndTipoAndUtilizadoFalseOrderByIdAsc(clienteId, TipoCupom.TROCA).stream()
                .filter(c -> !c.isVencido(hoje))
                .toList();
    }

    /** RN0033 / RN0037 — resolve os códigos informados na compra, rejeitando cupons inválidos. */
    @Transactional(readOnly = true)
    public List<Cupom> resolverParaCompra(Long clienteId, List<String> codigos) {
        List<Cupom> cupons = new ArrayList<>();
        if (codigos == null) return cupons;

        Set<String> vistos = new HashSet<>();
        LocalDate hoje = LocalDate.now();
        for (String bruto : codigos) {
            if (bruto == null || bruto.isBlank()) continue;
            String codigo = bruto.trim().toUpperCase();
            if (!vistos.add(codigo)) {
                throw new RegraNegocioException("RN0037", "O cupom " + codigo + " foi informado mais de uma vez.");
            }
            Cupom cupom = repository.findByCodigoIgnoreCase(codigo)
                    .orElseThrow(() -> new RegraNegocioException("RN0037", "Cupom " + codigo + " inválido: não existe no sistema."));
            if (cupom.isVencido(hoje)) {
                throw new RegraNegocioException("RN0037", "Cupom " + codigo + " está vencido.");
            }
            if (cupom.getTipo() == TipoCupom.TROCA) {
                if (!clienteId.equals(cupom.getClienteId())) {
                    throw new RegraNegocioException("RN0037", "Cupom de troca " + codigo + " não pertence a este cliente.");
                }
                if (cupom.isUtilizado()) {
                    throw new RegraNegocioException("RN0037", "Cupom de troca " + codigo + " já foi utilizado.");
                }
            }
            cupons.add(cupom);
        }

        long promocionais = cupons.stream().filter(c -> c.getTipo() == TipoCupom.PROMOCIONAL).count();
        if (promocionais > 1) {
            throw new RegraNegocioException("RN0033", "Apenas um cupom promocional pode ser utilizado por compra.");
        }
        return cupons;
    }

    /** Cancela um cupom de troca emitido para uma compra que acabou reprovada. */
    @Transactional
    public void cancelar(String codigo) {
        repository.findByCodigoIgnoreCase(codigo).ifPresent(cupom -> {
            cupom.marcarUtilizado();
            auditoria.registrar("Cupom", cupom.getId(), "UPDATE", "admin",
                    "Cupom de troca " + codigo + " cancelado (compra reprovada)");
        });
    }

    /** RN0036 — cupom de troca com a diferença entre os cupons usados e o valor da compra. */
    @Transactional
    public Cupom gerarCupomTroca(Long clienteId, BigDecimal valor, Long pedidoOrigemId) {
        String codigo = "TROCA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Cupom cupom = repository.save(new Cupom(codigo, TipoCupom.TROCA, valor, clienteId, null, pedidoOrigemId));
        auditoria.registrar("Cupom", cupom.getId(), "INSERT", "cliente-" + clienteId,
                "Cupom de troca " + codigo + " de R$ " + valor + " gerado como troco do pedido #" + pedidoOrigemId);
        return cupom;
    }
}
