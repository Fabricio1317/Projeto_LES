package com.games.vendas.config;

import com.games.cliente.adapter.in.web.dto.request.CadastrarClienteRequest;
import com.games.cliente.application.service.ClienteService;
import com.games.cliente.domain.entity.Cliente;
import com.games.cliente.domain.enums.Bandeira;
import com.games.cliente.domain.enums.GeneroCliente;
import com.games.vendas.adapter.out.persistence.repository.JogoRepository;
import com.games.vendas.adapter.out.persistence.repository.PedidoRepository;
import com.games.vendas.application.service.FreteService;
import com.games.vendas.domain.entity.EnderecoEntrega;
import com.games.vendas.domain.entity.Jogo;
import com.games.vendas.domain.entity.Pedido;
import com.games.vendas.domain.enums.StatusPedido;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Massa de dados da análise de vendas (RF0055): compras de várias categorias, distribuídas pelos
 * últimos seis meses e pelo mês atual, em status distintos (ENTREGUE, EM TRANSPORTE, APROVADA,
 * REPROVADA e TROCADO). As datas são relativas ao dia da subida, então o gráfico sempre tem histórico.
 * Só é carregada com o banco sem compras.
 */
@Component
@Order(2)
public class CargaHistoricoVendas implements ApplicationRunner {

    private final PedidoRepository pedidoRepository;
    private final JogoRepository jogoRepository;
    private final ClienteService clienteService;
    private final FreteService freteService;
    private Map<String, List<Jogo>> jogosPorCategoria;
    private int sequencia;

    public CargaHistoricoVendas(PedidoRepository pedidoRepository, JogoRepository jogoRepository,
                                ClienteService clienteService, FreteService freteService) {
        this.pedidoRepository = pedidoRepository;
        this.jogoRepository = jogoRepository;
        this.clienteService = clienteService;
        this.freteService = freteService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (pedidoRepository.count() > 0) return;
        jogosPorCategoria = jogoRepository.findAllByOrderByTituloAsc().stream().collect(Collectors.groupingBy(Jogo::getGenero));
        Cliente cliente = clienteService.cadastrar(new CadastrarClienteRequest(GeneroCliente.FEMININO,
                "Cliente Histórico de Vendas", LocalDate.of(1990, 5, 20), "529.982.247-25",
                "Celular", "11", "987654321", "historico.vendas@nexus.com.br", "Historico@2026", "Historico@2026",
                "Casa", "Rua", "Rua dos Jogadores", "42", "Centro", "01310100", "São Paulo", "SP", "Brasil"));

        // meses atrás, dia, status e itens ("categoria:quantidade")
        compra(cliente, 6, 8, StatusPedido.ENTREGUE, "RPG:1", "Ação:1");
        compra(cliente, 6, 19, StatusPedido.ENTREGUE, "Esporte:2");
        compra(cliente, 6, 25, StatusPedido.REPROVADA, "RPG:2");
        compra(cliente, 5, 5, StatusPedido.ENTREGUE, "Aventura:1", "RPG:1");
        compra(cliente, 5, 14, StatusPedido.ENTREGUE, "Corrida:1");
        compra(cliente, 5, 27, StatusPedido.TROCADO, "Esporte:1");
        compra(cliente, 4, 3, StatusPedido.ENTREGUE, "RPG:2");
        compra(cliente, 4, 16, StatusPedido.ENTREGUE, "Ação:1", "Plataforma:1");
        compra(cliente, 4, 22, StatusPedido.REPROVADA, "Aventura:1");
        compra(cliente, 3, 9, StatusPedido.ENTREGUE, "Esporte:1", "Sandbox:2");
        compra(cliente, 3, 18, StatusPedido.ENTREGUE, "Aventura:2");
        compra(cliente, 3, 24, StatusPedido.TROCADO, "Ação:1");
        compra(cliente, 2, 6, StatusPedido.ENTREGUE, "RPG:1", "Corrida:1");
        compra(cliente, 2, 15, StatusPedido.ENTREGUE, "Ação:2");
        compra(cliente, 2, 23, StatusPedido.REPROVADA, "Esporte:1");
        compra(cliente, 1, 4, StatusPedido.ENTREGUE, "Aventura:1", "Esporte:1");
        compra(cliente, 1, 12, StatusPedido.ENTREGUE, "RPG:1", "Sandbox:1");
        compra(cliente, 1, 20, StatusPedido.EM_TRANSPORTE, "RPG:1");
        compra(cliente, 1, 26, StatusPedido.APROVADA, "Ação:1");
        compra(cliente, 0, 1, StatusPedido.ENTREGUE, "Plataforma:1", "RPG:1");
        compra(cliente, 0, 1, StatusPedido.EM_TRANSPORTE, "Aventura:1");
    }

    private void compra(Cliente c, int mesesAtras, int dia, StatusPedido status, String... itens) {
        EnderecoEntrega endereco = new EnderecoEntrega("Residencial", c.getEnderecoTipoResidencia(),
                c.getEnderecoTipoLogradouro(), c.getEnderecoLogradouro(), c.getEnderecoNumero(), c.getEnderecoBairro(),
                c.getEnderecoCep(), c.getEnderecoCidade(), c.getEnderecoEstado(), c.getPais());
        Map<Jogo, Integer> quantidades = new LinkedHashMap<>();
        for (String item : itens) {
            String[] partes = item.split(":");
            List<Jogo> jogos = jogosPorCategoria.get(partes[0]);
            quantidades.put(jogos.get(sequencia++ % jogos.size()), Integer.parseInt(partes[1]));
        }
        BigDecimal peso = quantidades.entrySet().stream()
                .map(e -> e.getKey().getPesoKg().multiply(BigDecimal.valueOf(e.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Pedido pedido = new Pedido(gerarCodigo(), c.getId(), endereco, freteService.calcular(peso, c.getEnderecoEstado()));
        quantidades.forEach(pedido::adicionarItem);

        boolean recusado = status == StatusPedido.REPROVADA;
        pedido.adicionarPagamentoCartao(null, recusado ? "4000000000000000" : "4111111111111111",
                "CLIENTE HISTORICO", Bandeira.VISA, pedido.getTotal());
        pedido.getPagamentos().forEach(p -> p.registrarRetornoOperadora(!recusado));
        aplicarStatus(pedido, status);

        Pedido salvo = pedidoRepository.save(pedido);
        LocalDateTime data = YearMonth.now().minusMonths(mesesAtras).atDay(dia).atTime(10, 0);
        pedidoRepository.definirDataCompra(salvo.getId(), data.isAfter(LocalDateTime.now()) ? LocalDateTime.now().minusMinutes(1) : data);
    }

    private static void aplicarStatus(Pedido pedido, StatusPedido status) {
        if (status == StatusPedido.REPROVADA) {
            pedido.reprovar("Cartão **** 0000 recusado pela operadora");
            return;
        }
        pedido.aprovar();
        if (status == StatusPedido.APROVADA) return;
        pedido.despachar();
        if (status == StatusPedido.EM_TRANSPORTE) return;
        pedido.confirmarEntrega();
        if (status == StatusPedido.TROCADO) {
            pedido.entrarEmTroca();
            pedido.autorizarTroca();
            pedido.concluirTroca();
        }
    }

    private String gerarCodigo() {
        return "PED-" + Year.now() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
