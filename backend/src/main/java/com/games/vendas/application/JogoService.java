package com.games.vendas.application;

import com.games.cliente.application.AuditoriaService;
import com.games.cliente.application.exception.RecursoNaoEncontradoException;
import com.games.vendas.adapter.in.web.dto.JogoRequest;
import com.games.vendas.adapter.out.persistence.JogoRepository;
import com.games.vendas.domain.entity.Jogo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Catálogo usado pelo fluxo de vendas. O cadastro simplificado e o ajuste de
 * estoque existem para alimentar a loja e os testes; o CRUD completo de
 * jogos (RF0011–RF0016) e a entrada formal em estoque (RF0051) pertencem a
 * outras entregas.
 */
@Service
public class JogoService {

    public record JogoComDisponibilidade(Jogo jogo, int disponivel) {}

    private final JogoRepository repository;
    private final EstoqueService estoqueService;
    private final AuditoriaService auditoria;

    public JogoService(JogoRepository repository, EstoqueService estoqueService, AuditoriaService auditoria) {
        this.repository = repository;
        this.estoqueService = estoqueService;
        this.auditoria = auditoria;
    }

    /** Catálogo da loja: a disponibilidade desconta os bloqueios de outros clientes (RN0044). */
    @Transactional(readOnly = true)
    public List<JogoComDisponibilidade> listarCatalogo(Long clienteId) {
        return repository.findByAtivoTrueOrderByTituloAsc().stream()
                .map(l -> new JogoComDisponibilidade(l, estoqueService.disponivelPara(l, clienteId)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JogoComDisponibilidade> listarEstoque() {
        return repository.findAllByOrderByTituloAsc().stream()
                .map(l -> new JogoComDisponibilidade(l, estoqueService.disponivelGeral(l)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Jogo buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Jogo não encontrado: id=" + id));
    }

    @Transactional
    public Jogo cadastrar(JogoRequest req) {
        Jogo jogo = repository.save(new Jogo(req.titulo(), req.plataforma(), req.genero(), req.desenvolvedora(),
                req.distribuidora(), req.classificacaoIndicativa(), req.ano(), req.codigoBarras(),
                req.preco(), req.estoque(), req.pesoKg()));
        auditoria.registrar("Jogo", jogo.getId(), "INSERT", "admin",
                "Jogo cadastrado: " + jogo.getTitulo() + ", estoque=" + jogo.getEstoque());
        return jogo;
    }

    @Transactional
    public Jogo ajustarEstoque(Long id, int novoEstoque) {
        Jogo jogo = buscar(id);
        int anterior = jogo.getEstoque();
        jogo.ajustarEstoque(novoEstoque);
        jogo = repository.save(jogo);
        auditoria.registrar("Jogo", jogo.getId(), "UPDATE", "admin",
                "Estoque de " + jogo.getTitulo() + " ajustado de " + anterior + " para " + novoEstoque);
        return jogo;
    }
}
