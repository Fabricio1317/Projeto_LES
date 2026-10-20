package com.games.vendas.application.service;

import com.games.cliente.application.service.AuditoriaService;
import com.games.vendas.adapter.out.persistence.repository.NotificacaoRepository;
import com.games.vendas.domain.entity.Notificacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Notificações exibidas ao cliente na loja, como a de troca autorizada (RNF0046). */
@Service
public class NotificacaoService {

    private final NotificacaoRepository repository;
    private final AuditoriaService auditoria;

    public NotificacaoService(NotificacaoRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    @Transactional
    public Notificacao notificar(Long clienteId, String mensagem) {
        Notificacao notificacao = repository.save(new Notificacao(clienteId, mensagem));
        auditoria.registrar("Notificacao", notificacao.getId(), "INSERT", "sistema",
                "Notificação ao cliente-" + clienteId + ": " + mensagem);
        return notificacao;
    }

    @Transactional(readOnly = true)
    public List<Notificacao> listar(Long clienteId) {
        return repository.findByClienteIdOrderByDataHoraDescIdDesc(clienteId);
    }

    @Transactional
    public void marcarTodasComoLidas(Long clienteId) {
        List<Notificacao> naoLidas = repository.findByClienteIdAndLidaFalse(clienteId);
        naoLidas.forEach(Notificacao::marcarComoLida);
        if (!naoLidas.isEmpty()) {
            auditoria.registrar("Notificacao", 0L, "UPDATE", "cliente-" + clienteId,
                    naoLidas.size() + " notificação(ões) marcada(s) como lida(s)");
        }
    }
}
