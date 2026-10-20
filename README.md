# Nexus — Clientes e Vendas (backend + frontend + testes automatizados)

Dois módulos do Nexus, com testes automatizados de interface (Cypress)
cobrindo os RF/RN/RNF do DRS:

- **Gestão de clientes** — cadastrar, consultar, alterar e inativar
  clientes, mais endereços (RF0026), cartões de crédito (RF0027), troca de
  senha (RF0028) e consulta de transações (RF0025).
- **Fluxo de vendas, de ponta a ponta** — carrinho com bloqueio temporário,
  compra e pagamento (cartões e cupons), validação automática do pagamento
  (APROVADA/REPROVADA), despacho, entrega e o ciclo completo da troca
  (solicitação, autorização com notificação, recebimento, cupom de troca e
  reentrada no estoque).

## Estrutura

```
nexus-crud/
├── backend/                     # Spring Boot (Java 21)
│   ├── pom.xml
│   └── src/main/java/com/games/
│       ├── cliente/
│       │   ├── NexusClienteApplication.java    # ponto de entrada (sobe os módulos de Cliente e Vendas)
│       │   ├── domain/
│       │   │   ├── entity/                     # Cliente, Endereco, Cartao, LogTransacao (entidades JPA)
│       │   │   └── enums/                      # GeneroCliente, StatusCliente, TipoEndereco, Bandeira
│       │   ├── application/
│       │   │   ├── service/                    # ClienteService, EnderecoService, CartaoService, AuditoriaService
│       │   │   ├── validation/                 # CpfValidator, SenhaValidator
│       │   │   └── exception/                  # RegraNegocioException, RecursoNaoEncontradoException, ClienteNaoEncontradoException
│       │   └── adapter/
│       │       ├── in/web/
│       │       │   ├── controller/             # ClienteController, EnderecoController, CartaoController, AuditoriaController
│       │       │   ├── handler/                # GlobalExceptionHandler (erros → resposta HTTP com o código da regra)
│       │       │   └── dto/request/ e dto/response/
│       │       └── out/persistence/
│       │           └── repository/             # ClienteRepository, EnderecoRepository, CartaoRepository, LogTransacaoRepository
│       └── vendas/
│           ├── domain/
│           │   ├── entity/                     # Jogo, Carrinho, CarrinhoItem, Pedido, PedidoItem, PagamentoCartao, PedidoCupom, Cupom, EnderecoEntrega, ParametroSistema, Troca, TrocaItem, Notificacao
│           │   └── enums/                      # StatusPedido, StatusTroca, TipoTroca, TipoCupom, SituacaoItemCarrinho
│           ├── application/
│           │   ├── service/                    # CarrinhoService, PedidoService, TrocaService, NotificacaoService, CupomService, JogoService, EstoqueService, FreteService, ParametroService
│           │   ├── event/                      # CompraFinalizadaEvent
│           │   ├── listener/                   # ValidacaoAutomaticaPagamento (validação automática do pagamento)
│           │   └── gateway/                    # OperadoraCartaoSimulada (operadora de cartão simulada)
│           ├── adapter/
│           │   ├── in/web/
│           │   │   ├── controller/             # CarrinhoController, PedidoController, TrocaController, NotificacaoController, CupomController, JogoController, ParametroController
│           │   │   └── dto/request/ e dto/response/
│           │   └── out/persistence/
│           │       └── repository/             # repositórios Spring Data JPA
│           └── config/                         # CargaInicialDominio (RNF0013)
│       resources/                              # application.properties, static/ (frontend)
│       test/java/.../application/service/      # testes unitários (JUnit + Mockito) do ClienteService
├── cypress/
│   ├── e2e/cliente-crud.cy.js          # suíte do módulo de clientes (37 testes)
│   ├── e2e/pedido-caminho-feliz.cy.js  # criação de pedido — suíte da fase anterior (23 testes)
│   ├── e2e/venda-ponta-a-ponta.cy.js   # venda do carrinho ao fechamento da troca — roteiro mínimo 1–11 (27 testes)
│   └── support/                        # cpf-generator.js e vendas-helpers.js (pré-condições e ações comuns)
├── cypress.config.js
└── package.json
```

## Como executar

### 1. Backend

```bash
cd backend
mvn spring-boot:run
```

Testes unitários (JUnit + Mockito), funcionam em JDK 21 ou mais novo:

```bash
cd backend
mvn test
```

### 2. Testes automatizados de interface (Cypress)

Com o backend rodando em outro terminal (o frontend é servido pelo próprio Spring Boot):

```bash
npm install
npx cypress open     # modo interativo, bom para a apresentação ao vivo
# ou
npx cypress run      # modo headless, roda todas as suítes
npx cypress run --spec cypress/e2e/venda-ponta-a-ponta.cy.js    # só a venda de ponta a ponta (apresentação)
npx cypress run --spec cypress/e2e/pedido-caminho-feliz.cy.js   # só a criação de pedido (fase anterior)
```

Rode **uma única instância** do backend: com `ddl-auto=create-drop`, uma
segunda instância apontando para o mesmo banco apaga as tabelas da primeira.

**Velocidade dos testes.** Para a apresentação, os testes rodam com uma pausa
de 200 ms após cada clique, seleção, marcação ou digitação, e digitam mais
devagar (`cypress/support/e2e.js`). Para mudar:

```bash
npx cypress open --env lentidao=1000   # ainda mais lento
npx cypress run --env lentidao=0       # sem pausas (rápido, para conferir tudo de uma vez)
```

O valor padrão fica em `cypress.config.js` (`env.lentidao`).

Na interface, a aba **Loja** é a visão do cliente (escolha o cliente
comprando no topo, já que o módulo não tem login): carrinho, compra,
minhas compras (com "Solicitar troca"), minhas trocas, meus cupons de troca
e notificações. A aba **Gestão de vendas** é a visão do administrador:
compras (despachar e confirmar entrega), pedidos de troca (autorizar e
confirmar recebimento), estoque, cupons e parâmetros. A validação do
pagamento não tem botão: é feita pelo sistema, e as telas se atualizam
sozinhas enquanto há compra EM PROCESSAMENTO.

## Rastreabilidade — Clientes (`cliente-crud.cy.js`)

| RF/RN/RNF | Regra | Endpoint | Teste Cypress |
|---|---|---|---|
| RF0021 / RN0026 | Cadastro com campos obrigatórios (gênero, nome, data de nascimento, CPF, telefone, e-mail, senha, endereço residencial) | `POST /api/clientes` | "deve cadastrar um cliente com todos os campos obrigatórios válidos" |
| RN0026 | Nome obrigatório | `POST /api/clientes` | "não deve cadastrar cliente sem o nome" |
| RN0026 | Endereço residencial obrigatório (CEP) | `POST /api/clientes` | "não deve cadastrar cliente sem endereço residencial (CEP obrigatório)" |
| RN0026 | CPF e data de nascimento obrigatórios | `POST /api/clientes` | "não deve cadastrar cliente sem CPF e Data de Nascimento" |
| RN0026 | Telefone (tipo + DDD + número, campos separados) e e-mail obrigatórios | `POST /api/clientes` | "não deve cadastrar cliente sem telefone e e-mail" |
| RNF0031 | Senha forte | `POST /api/clientes`, `PATCH /api/clientes/{id}/senha` | "não deve cadastrar com senha fraca" / "não deve alterar para uma nova senha fraca" |
| RNF0032 | Confirmação de senha | `POST /api/clientes` | "não deve cadastrar quando a confirmação diverge" |
| RNF0033 | Senha criptografada (nunca em texto puro) | `POST /api/clientes` | validado no teste unitário `deveCadastrarClienteComDadosValidos` |
| RNF0035 | Código único de cliente | `POST /api/clientes` | validado no teste unitário (`getCodigoCliente().startsWith("CLI-")`) |
| — | CPF inválido / duplicado | `POST /api/clientes` | "não deve cadastrar com CPF inválido" / "...com o mesmo CPF" |
| RF0024 | Consulta com filtros combinados/isolados por nome, CPF, e-mail, código do cliente e status | `GET /api/clientes` | "deve listar" / "por nome" / "por CPF" / "pelo código do cliente" / "combinar filtros (nome + status)" |
| RF0022 | Alterar dados cadastrais (nome, telefone, e-mail, endereço) | `PUT /api/clientes/{id}` | "deve alterar nome, telefone, e-mail e endereço" |
| RF0028 | Alterar senha isoladamente, sem tocar nos demais dados | `PATCH /api/clientes/{id}/senha` | "deve alterar a senha do cliente com sucesso" / "senha atual incorreta" |
| RF0023 | Inativar cliente | `PATCH /api/clientes/{id}/inativar` | "deve inativar um cliente ativo" |
| RN — | Não permitir inativar um cliente já inativo | `PATCH /api/clientes/{id}/inativar` | "não deve permitir inativar um cliente que já está inativo" |
| — | Distinção inativação × exclusão (registro continua existindo e consultável) | `PATCH /api/clientes/{id}/inativar` | "a inativação NÃO deve excluir o cliente" |
| RF0026 | Cadastrar endereço (múltiplos, cobrança/entrega/ambos, apelido de identificação) | `POST /api/clientes/{id}/enderecos` | "deve adicionar um endereço de cobrança ao cliente" |
| RN0023 | Tipo do endereço obrigatório | `POST /api/clientes/{id}/enderecos` | "não deve adicionar endereço sem o tipo selecionado" |
| RF0026 / RN0023 | Apelido (frase curta de identificação) obrigatório | `POST /api/clientes/{id}/enderecos` | "não deve adicionar endereço sem a frase curta de identificação" |
| RN0023 | Logradouro, número, bairro e cidade obrigatórios | `POST /api/clientes/{id}/enderecos` | "não deve adicionar endereço faltando logradouro, número, bairro ou cidade" |
| RN0021 | Não remover o único endereço de cobrança | `DELETE /api/clientes/{id}/enderecos/{id}` | "não deve permitir remover o único endereço de cobrança" |
| RN0022 | Não remover o único endereço de entrega | `DELETE /api/clientes/{id}/enderecos/{id}` | "não deve permitir remover o único endereço de entrega" |
| RN0021 / RN0022 | Remover endereço quando não é o último do seu tipo | `DELETE /api/clientes/{id}/enderecos/{id}` | "deve remover um endereço quando não é o último do seu tipo" |
| RNF0034 | Alteração isolada de **endereço**, sem editar os demais dados do cliente | `PUT /api/clientes/{id}/enderecos/{id}` | citado no javadoc de `Endereco.atualizar()` |
| RF0027 / RN0024 | Cadastrar cartão (número, nome impresso, bandeira, código de segurança) | `POST /api/clientes/{id}/cartoes` | "deve adicionar um cartão e exibir o número mascarado" |
| RN0024 | Campos obrigatórios do cartão (nome impresso, código de segurança) | `POST /api/clientes/{id}/cartoes` | "não deve adicionar cartão faltando dados obrigatórios (Nome Impresso e CVV)" |
| RN0025 | Bandeira deve ser uma das aceitas pelo sistema | `POST /api/clientes/{id}/cartoes` | "não deve adicionar cartão com bandeira em branco ou não permitida" |
| RF0027 | Primeiro cartão cadastrado é automaticamente o preferencial | `POST /api/clientes/{id}/cartoes` | "o primeiro cartão cadastrado deve ser automaticamente o preferencial" |
| RF0027 | Troca de cartão preferencial | `PATCH /api/clientes/{id}/cartoes/{id}/preferencial` | "deve trocar o cartão preferencial, desmarcando o anterior" |
| RF0027 | Remover cartão | `DELETE /api/clientes/{id}/cartoes/{id}` | "deve remover um cartão" |
| RF0027 | Promoção automática ao remover o preferencial | `DELETE /api/clientes/{id}/cartoes/{id}` | "ao remover o cartão preferencial, outro cartão restante deve assumir o posto" |
| RNF0011 | Consultas respondidas em até 1s | `GET /api/clientes` | não medido em teste automatizado; ver índices em `nome`/`status` (Cliente) e `cliente_id` (Endereco/Cartao) em `@Table(indexes = ...)` |
| RNF0012 | Log de auditoria (data, hora, usuário, dado alterado) em toda inserção/alteração | `GET /api/auditoria` | "deve registrar no log de auditoria o cadastro de um novo cliente" / "...a inativação de um cliente" |
| RF0025 | Consulta das transações realizadas pelo cliente | `GET /api/clientes/{id}/pedidos` | *(venda-ponta-a-ponta)* "a consulta de clientes exibe as transações realizadas pelo cliente" |
| RN0027 | Ranking numérico pelo perfil de compra | validação automática do pagamento (`ValidacaoAutomaticaPagamento`) | *(venda-ponta-a-ponta)* "o ranking do cliente é atualizado pela compra aprovada" |

## Criação de pedido — caminho feliz (`pedido-caminho-feliz.cy.js`)

Suíte da fase anterior (criação de pedido), na **numeração do DRS**.
Escopo: RF0031–RF0038, RN0023–RN0025, RN0031, RN0033–RN0036, RNF0011 e
RNF0012. Como agora o pagamento é validado automaticamente logo após a
finalização, o status EM PROCESSAMENTO é conferido no retorno da
finalização e no log de auditoria.

### Roteiro mínimo da apresentação → teste

| Roteiro | Teste Cypress |
|---|---|
| 1. Mais de um item no carrinho e alteração da quantidade | "deve incluir mais de um jogo no carrinho, definindo a quantidade na adição e alterando-a na visualização" |
| 2. Compra com endereço e cartão previamente cadastrados | "deve finalizar uma compra com endereço e cartão previamente cadastrados" |
| 3. Novo endereço e novo cartão incorporados ao perfil | "deve finalizar com novo endereço e novo cartão cadastrados durante a compra e incorporá-los ao perfil" |
| 4. Mais de um cartão respeitando o mínimo | "deve pagar com mais de um cartão de crédito, com no mínimo R$ 10,00 em cada" |
| 5. Cartão e cupons, inclusive cartão abaixo de R$ 10,00 | "deve pagar com cartão de crédito e cupom promocional" / "deve combinar cupons de troca e promocional com cartão, aceitando menos de R$ 10,00 no cartão" |
| 6. Cupons acima do valor, com emissão de cupom de troca | "deve emitir um cupom de troca com a diferença quando os cupons superam o valor da compra" |
| 7. Pedido registrado EM PROCESSAMENTO | "o pedido finalizado é registrado com status EM PROCESSAMENTO e o carrinho é esvaziado" |

### Rastreabilidade RF/RN/RNF → teste

| RF/RN/RNF | Regra | Endpoint | Teste Cypress |
|---|---|---|---|
| RF0031 / RF0032 | Vários itens no carrinho; quantidade definida na adição e alterada na visualização | `POST` / `PUT /api/clientes/{id}/carrinho/itens` | "deve incluir mais de um jogo no carrinho…" |
| RF0031 | Excluir item mantendo os demais | `DELETE /api/clientes/{id}/carrinho/itens/{itemId}` | "deve excluir um item do carrinho mantendo os demais" |
| RN0031 | Não adicionar acima do disponível, não alterar para acima do disponível, não adicionar item indisponível | `POST` / `PUT /api/clientes/{id}/carrinho/itens` | os três testes "RN0031 — …" |
| RF0033 / RF0035 / RF0036 / RF0038 | Compra iniciada no carrinho, com endereço e cartão já cadastrados | `POST /api/clientes/{id}/pedidos` | "deve finalizar uma compra com endereço e cartão previamente cadastrados" |
| RF0035 / RF0036 | Novo endereço e novo cartão na compra, incorporados ao perfil | `POST /api/clientes/{id}/pedidos` | "deve finalizar com novo endereço e novo cartão…" |
| RN0023 | Composição obrigatória do novo endereço | `POST /api/clientes/{id}/pedidos` | "não deve finalizar com um novo endereço sem os campos obrigatórios" |
| RN0024 | Composição obrigatória do novo cartão | `POST /api/clientes/{id}/pedidos` | "não deve finalizar com um novo cartão sem nome impresso e código de segurança" |
| RN0025 | Novo cartão só com bandeira registrada | `POST /api/clientes/{id}/pedidos` | "o novo cartão só aceita bandeiras registradas no sistema" |
| RF0034 | Frete pelos itens (peso) e pelo endereço (UF) | `POST /api/clientes/{id}/carrinho/frete` | os dois testes "RF0034 — …" |
| RN0034 | Mais de um cartão, mínimo de R$ 10,00 por cartão | `POST /api/clientes/{id}/pedidos` | "deve pagar com mais de um cartão…" / "não deve aceitar um cartão com valor abaixo de R$ 10,00…" |
| RF0036 | Valores dos cartões fecham o valor a pagar | `POST /api/clientes/{id}/pedidos` | "não deve finalizar quando a soma dos cartões difere do valor a pagar" |
| RF0037 | Cartão + cupom promocional | `POST /api/clientes/{id}/pedidos` | "deve pagar com cartão de crédito e cupom promocional" |
| RF0037 / RN0035 | Cupons de troca e promocional + cartão abaixo de R$ 10,00 (valor máximo dos cupons primeiro) | `POST /api/clientes/{id}/pedidos` | "deve combinar cupons de troca e promocional com cartão…" |
| RN0033 | Apenas um cupom promocional por compra | `POST /api/clientes/{id}/cupons/validar` | "não deve permitir mais de um cupom promocional na mesma compra" |
| RN0036 | Emissão de cupom de troca com a diferença, na finalização | `POST /api/clientes/{id}/pedidos` | "deve emitir um cupom de troca com a diferença…" |
| RN0036 | Cupons desnecessários recusados | `POST /api/clientes/{id}/pedidos` | "não deve permitir o uso de cupons desnecessários" |
| RF0038 | Pedido registrado EM PROCESSAMENTO, carrinho esvaziado | `POST /api/clientes/{id}/pedidos` | "o pedido finalizado é registrado com status EM PROCESSAMENTO…" |
| RNF0011 | Consultas do fluxo respondem em até 1 segundo | catálogo, carrinho, endereços, cartões, cupons, frete, clientes, pedidos | "as consultas usadas na criação do pedido respondem em no máximo 1 segundo" |
| RNF0012 | Escritas do fluxo (carrinho, endereço, cartão, pedido) no log | `GET /api/auditoria` | "as operações de escrita da compra são registradas no log de auditoria" |

## Venda ponta a ponta — do carrinho ao fechamento da troca (`venda-ponta-a-ponta.cy.js`)

Suíte da apresentação desta atividade. Os nomes dos testes e as tabelas
abaixo usam a **numeração da atividade**.

### Numeração da atividade × DRS

O enunciado desta atividade numera alguns requisitos de forma diferente
do DRS (a fase anterior seguiu o DRS). Os códigos de regra retornados pela
API e os nomes dos testes desta suíte seguem a atividade; a correspondência é:

| Atividade | DRS | Requisito |
|---|---|---|
| RF0036 | RF0036 + RF0037 | Forma de pagamento: cartões, cupons de troca, cupom promocional ou combinação |
| RF0037 | RF0038 | Finalização da compra (status EM PROCESSAMENTO) |
| RF0038 | RF0039 | Despacho (APROVADA → EM TRANSPORTE) |
| RF0039 | RF0040 | Confirmação da entrega (EM TRANSPORTE → ENTREGUE) |
| RF0040 | RF0041 | Solicitação de troca |
| RF0041 | RF0042 | Autorização da troca |
| RF0042 | RF0043 | Visualização dos pedidos de troca |
| RF0043 | RF0044 | Confirmação do recebimento dos itens da troca |
| RF0044 | RF0045 | Geração do cupom de troca |
| RNF0045 | RN0045 | Ao desbloquear, retirar do carrinho todos os itens do mesmo produto |
| RNF0046 | RN0046 | Notificação ao cliente quando a troca é autorizada |

> Vale confirmar com o professor qual numeração deve aparecer na apresentação.

### Roteiro mínimo de demonstração → teste

| Roteiro | Teste Cypress |
|---|---|
| 1. Compra aprovada com um cartão | "a compra nasce EM PROCESSAMENTO, o sistema valida o pagamento, aprova e dá baixa no estoque" |
| 2. Dois ou mais cartões, tentativa abaixo de R$ 10,00 bloqueada | "bloqueia valor abaixo de R$ 10,00 em um dos cartões e aprova a compra com no mínimo R$ 10,00 em cada" |
| 3. Cupom promocional e cartão com menos de R$ 10,00 (permitido) | "com cupom promocional, o único cartão pode pagar menos de R$ 10,00" |
| 4. Cupons de troca acima do valor da compra, com cupom da diferença | "a compra paga com cupons de troca de valor superior gera um cupom de troca com a diferença" |
| 5. Compra REPROVADA, itens desbloqueados e sem baixa de estoque | "a compra recusada pela operadora fica REPROVADA, os itens são desbloqueados e o estoque não sofre baixa" |
| 6. Expiração do bloqueio, remoção dos itens e botão de compra desativado | "ao expirar o bloqueio, os itens saem do carrinho, aparecem como removidos e a compra fica desabilitada" |
| 7. Administrador: APROVADA → EM TRANSPORTE → ENTREGUE | "o administrador despacha a compra APROVADA (EM TRANSPORTE) e confirma a entrega (ENTREGUE)" |
| 8. Troca de pedido que não está ENTREGUE (bloqueada) | "bloqueia a solicitação de troca de um pedido que não está ENTREGUE" |
| 9. Troca de um item e troca da compra inteira | "o cliente solicita a troca de um item de um pedido ENTREGUE" / "o cliente solicita a troca da compra inteira e o pedido passa a EM TROCA" |
| 10. Fluxo da troca: solicitação, visualização, autorização com notificação, recebimento, cupom e reentrada | "solicitação, visualização, autorização com notificação, recebimento, cupom e reentrada no estoque" |
| 11. Uso do cupom de troca gerado em nova compra | "o cupom gerado pela troca é usado como pagamento em uma nova compra" |

### Ciclo de vida dos status

| De → para | Quem executa | Requisito | Onde |
|---|---|---|---|
| Compra finalizada → EM PROCESSAMENTO | Sistema | RF0037 | `POST /api/clientes/{id}/pedidos` |
| EM PROCESSAMENTO → APROVADA / REPROVADA | Sistema (validação automática) | RN0037 / RN0038 | `ValidacaoAutomaticaPagamento` → `PedidoService.validarPagamento` |
| APROVADA → EM TRANSPORTE | Administrador | RF0038 / RN0039 | `PATCH /api/pedidos/{id}/despachar` |
| EM TRANSPORTE → ENTREGUE | Administrador | RF0039 / RN0040 | `PATCH /api/pedidos/{id}/confirmar-entrega` |
| ENTREGUE → EM TROCA | Cliente solicita, sistema registra | RF0040 / RN0041 / RN0043 | `POST /api/clientes/{id}/pedidos/{pedidoId}/trocas` |
| EM TROCA → TROCA AUTORIZADA | Administrador | RF0041 (+ notificação RNF0046) | `PATCH /api/trocas/{id}/autorizar` |
| TROCA AUTORIZADA → TROCADO | Administrador confirma o recebimento | RF0043 / RN0042 (+ cupom RF0044, reentrada RF0054) | `PATCH /api/trocas/{id}/confirmar-recebimento` |

### Rastreabilidade RF/RN/RNF → teste

| RF/RN/RNF | Regra | Teste Cypress |
|---|---|---|
| RF0031 / RF0032 / RN0031 | Adicionar e alterar itens do carrinho validando o estoque | suíte `pedido-caminho-feliz.cy.js` (Roteiro 1) |
| RN0044 | Bloqueio temporário com prazo parametrizado, contado a partir do último item; bloqueio para os demais clientes; aviso a 5 minutos | Roteiro 6: "itens no carrinho de um cliente ficam bloqueados…" / "avisa o cliente quando faltam 5 minutos…" |
| RNF0045 / RNF0042 | Ao desbloquear, todos os itens do produto saem do carrinho; itens removidos exibidos, compra desabilitada e itens devolvidos ao carrinho | Roteiro 6: "ao expirar o bloqueio…" |
| RF0033 / RF0034 / RF0035 | Compra a partir do carrinho, frete e endereço | Roteiros 1–4 e suíte `pedido-caminho-feliz.cy.js` |
| RF0036 / RN0034 | Um ou mais cartões, mínimo de R$ 10,00 por cartão | Roteiro 2 |
| RN0033 | Apenas um cupom promocional por compra | "não permite mais de um cupom promocional na mesma compra" |
| RN0035 | Valor máximo dos cupons; cartão abaixo de R$ 10,00 só nesse caso | Roteiro 3 |
| RN0036 | Cupom de troca com a diferença; cupons desnecessários recusados | Roteiro 4 (dois testes) |
| RN0032 | Revalidação do estoque entre o carrinho e a finalização | os dois testes "RN0032 — …" |
| RF0037 | Compra registrada EM PROCESSAMENTO | Roteiro 1 (mensagem e log) |
| RN0037 / RN0038 | Validação automática (cupons e operadora) → APROVADA ou REPROVADA | Roteiros 1 e 5; "não aceita cupom vencido nem cupom inexistente" |
| RF0053 / RN0028 | Baixa no estoque só na aprovação; itens de compra reprovada desbloqueados | Roteiros 1 e 5 |
| RF0038 / RN0039 | Despacho somente de compras APROVADAS | Roteiro 7 (dois testes) |
| RF0039 / RN0040 | Entrega somente de compras EM TRANSPORTE | Roteiro 7 (dois testes) |
| RN0043 | Troca somente de pedidos ENTREGUE | Roteiro 8 |
| RF0040 / RN0041 | Troca de um item ou da compra inteira; limite pela quantidade comprada | Roteiro 9 (três testes) |
| RF0042 | Administrador visualiza os pedidos de troca EM TROCA | Roteiro 10 |
| RF0041 / RNF0046 | Autorização da troca e notificação ao cliente | Roteiro 10 |
| RF0043 / RN0042 | Confirmação do recebimento → TROCADO (só depois da autorização) | Roteiro 10 (três testes) |
| RF0044 | Cupom de troca gerado no recebimento, consultado e usado em nova compra | Roteiros 10 e 11 |
| RF0054 | Reentrada dos itens trocados no estoque (opcional, indicada pelo administrador) | Roteiro 10 |
| RF0025 / RN0027 | Transações e ranking do cliente | "a consulta de clientes exibe as transações…" / "o ranking do cliente é atualizado pela compra aprovada…" |
| RNF0011 | Consultas do pós-venda em até 1 segundo | "as consultas do pós-venda … respondem em no máximo 1 segundo" |
| RNF0012 | Mudanças de status, troca, cupom e reentrada no log de auditoria | "as mudanças de status, a troca, o cupom e a reentrada no estoque são registrados no log de auditoria" |
| RNF0013 | Carga de domínio na implantação (parâmetros, jogos, cupons promocionais) | `CargaInicialDominio` roda a cada subida e só insere o que falta |

### Endpoints do pós-venda

| Método e caminho | Uso |
|---|---|
| `GET /api/pedidos?status=` · `GET /api/pedidos/{id}` | Consulta das compras (administrador) |
| `GET /api/clientes/{id}/pedidos` | Compras do cliente e seus status |
| `PATCH /api/pedidos/{id}/despachar` · `PATCH /api/pedidos/{id}/confirmar-entrega` | Despacho e entrega |
| `POST /api/clientes/{id}/pedidos/{pedidoId}/trocas` | Solicitar troca: `{ "compraInteira": true }` ou `{ "itens": [{ "pedidoItemId": 1, "quantidade": 1 }] }` |
| `GET /api/clientes/{id}/trocas` · `GET /api/trocas?status=` | Trocas do cliente · pedidos de troca (administrador) |
| `PATCH /api/trocas/{id}/autorizar` | Autorizar a troca (gera a notificação) |
| `PATCH /api/trocas/{id}/confirmar-recebimento` | Confirmar o recebimento: `{ "retornarEstoque": true }` |
| `GET /api/clientes/{id}/notificacoes` · `PATCH /api/clientes/{id}/notificacoes/lidas` | Notificações do cliente |
| `GET /api/clientes/{id}/cupons` | Cupons de troca disponíveis para o cliente |

## Produto: jogo no lugar de livro

O DRS descreve um e-commerce de livros; o Nexus é um e-commerce de jogos.
As regras de venda se aplicam sem mudança, trocando apenas o produto:

| DRS (livro) | Nexus (jogo) |
|---|---|
| Livro | `Jogo` (edição física, com peso para o frete) |
| Categoria | Gênero (Ação, RPG, Esporte…) |
| Autor / Editora | Desenvolvedora / Distribuidora |
| ISBN / Código de barras | Código de barras |
| — | Plataforma (PlayStation 5, Xbox Series X\|S, Nintendo Switch, PC) e classificação indicativa (L, 10, 12, 14, 16, 18) |

## Regras simuladas no fluxo de vendas

O DRS não define alguns valores concretos; as convenções abaixo estão
centralizadas no código e documentadas aqui para a apresentação:

- **Frete (RF0034)** — base por UF de entrega (SP: R$ 10,00; demais estados
  do Sul e Sudeste: R$ 15,00; demais: R$ 25,00) + R$ 5,00 por kg dos itens
  (`FreteService`). O critério também aparece na tela da compra.
- **Pagamento (RN0034–RN0036)** — os cupons são aplicados primeiro, pelo
  valor máximo; o restante vai para os cartões, cuja soma deve fechar o valor
  a pagar. Um cartão abaixo de R$ 10,00 só é aceito quando é o único cartão
  e completa um pagamento feito com cupons. Se os cupons superarem a compra,
  o cupom de troca com a diferença é emitido na finalização (desde que
  nenhum cupom seja desnecessário).
- **Cupons de troca** — gerados pelo sistema com a diferença de uma compra
  paga com cupons (RN0036) e no recebimento de uma troca (RF0044). Para
  preparar cenários, também podem ser cadastrados pela Gestão de vendas
  (cupom do tipo Troca) ou por `POST /api/cupons`.
- **Operadora de cartão (RN0037)** — simulada: recusa cartões cujo número
  termina em `0000` e aprova os demais (`OperadoraCartaoSimulada`).
- **Validação automática do pagamento (RN0037 / RN0038)** — ao finalizar
  uma compra, o `PedidoService` publica o evento `CompraFinalizadaEvent`;
  depois da gravação, a `ValidacaoAutomaticaPagamento` agenda a validação
  daquela compra para 3 s depois. Ela valida cupons (validade) e cartões
  (operadora) e define APROVADA (com baixa no estoque) ou REPROVADA (itens
  desbloqueados, cupons de uso único liberados e o cupom de diferença
  cancelado). Não há consulta periódica ao banco: sem compras novas, nada
  roda. Os 3 s deixam o status EM PROCESSAMENTO visível na tela e podem
  ser mudados pela propriedade opcional `nexus.pagamento.atraso-segundos`.
  Na subida da aplicação, compras que tenham ficado EM PROCESSAMENTO são
  validadas.
- **Troca (RF0040–RF0044, RN0041–RN0043)** — só para pedidos ENTREGUE. Na
  troca de itens, cada item selecionado vira um pedido de troca próprio
  (tipo "Item") e o pedido continua ENTREGUE para os demais itens; a
  quantidade é limitada ao que foi comprado menos o que já está em troca.
  Na troca da compra inteira, um único pedido de troca leva todos os
  itens e o próprio pedido acompanha os status (EM TROCA → TROCA
  AUTORIZADA → TROCADO); ela não é aceita se algum item do pedido já tiver
  troca. O cupom gerado vale o preço pago pelos itens devolvidos (sem
  frete). Ao confirmar o recebimento, o administrador indica se os itens
  voltam ao estoque (marcado por padrão — RF0054). Estorno no cartão e
  recusa da troca não fazem parte do escopo.
- **Notificações (RNF0046)** — a autorização da troca gera uma notificação
  para o cliente, exibida no topo da Loja (com contador de não lidas); o
  recebimento da troca gera outra, com o cupom disponível.
- **Bloqueio do carrinho (RN0044)** — o prazo é o parâmetro
  `PRAZO_BLOQUEIO_CARRINHO_SEGUNDOS` (padrão 1800 s), editável na Gestão de
  vendas. Conta a partir do último item incluído; o aviso aparece quando
  faltam 5 minutos ou menos.
- **Disponível para venda** — estoque físico − itens de compras EM
  PROCESSAMENTO − itens bloqueados nos carrinhos de outros clientes.
- **Ranking (RN0027)** — 1 ponto a cada R$ 100,00 em compras efetivadas
  (APROVADA, EM TRANSPORTE ou ENTREGUE).
- **Cupons promocionais iniciais (RNF0013)** — `PROMO10`, `PROMO20`,
  `GAMER5` e `VENCIDO10` (vencido, para demonstrar a RN0037).

## Sobre o telefone (RN0026)

O DRS exige que o telefone seja composto por tipo, DDD e número — por
isso o campo não é um texto livre único, e sim três campos separados
(`telefoneTipo`, `telefoneDdd`, `telefoneNumero`) tanto no cadastro
quanto na alteração de dados cadastrais, com validação de formato
(DDD com 2 dígitos, número com 8 ou 9 dígitos) no backend.

## Sobre o escopo de Endereço e Cartão

O "endereço residencial" pedido em RN0026 (parte do cadastro básico do
cliente) é diferente dos endereços de RF0026: aquele é um dado de
identificação do cliente; estes são os endereços de entrega/cobrança
usados nas compras, com múltiplos registros por cliente. Por isso o
Cliente mantém seus próprios campos de endereço residencial, e
Endereco/Cartão são entidades à parte, vinculadas por `clienteId`.

Para que RN0021 e RN0022 (ao menos um endereço de cobrança e um de
entrega) já valham a partir do cadastro — e não só depois que o cliente
adicionar um endereço manualmente em RF0026 — o endereço residencial
informado no cadastro é replicado automaticamente como o primeiro
registro da lista de endereços, com tipo `AMBOS` (apelido "Residencial").
Ele aparece normalmente na tela de "Endereços do cliente" e pode ser
removido como qualquer outro, desde que a remoção não deixe o cliente
sem cobrança ou sem entrega.

## Sobre o log de auditoria (RNF0012)

Como o sistema não implementa autenticação, o campo "usuário responsável"
é preenchido com o código do cliente nas ações feitas por ele (cadastro,
carrinho, compra, solicitação de troca), com `admin` nas ações da Gestão
de vendas (despachar, entregar, autorizar troca, confirmar recebimento,
reentrada no estoque, estoque, cupons, parâmetros) e com `sistema` nas
ações automáticas (validação do pagamento, notificações). Em um
sistema com login real, este valor viria do usuário autenticado na
sessão. A auditoria cobre inserções e alterações (INSERT/UPDATE),
conforme o texto literal do requisito; remoções não geram log.

## Fora do escopo desta entrega

- **Estorno e recusa de troca** — fora dos requisitos, conforme a atividade.
- **Cadastro de jogos** — RF0011–RF0016 e RN0011–RN0017 (no DRS, "livros"). Existe apenas
  um cadastro simplificado (`POST /api/jogos`) para alimentar a loja.
- **Controle de estoque** — RF0051/RF0052 e RN0050–RN0062. Existe apenas o
  ajuste manual de estoque na Gestão de vendas; a baixa por venda (RF0053)
  e a reentrada por troca (RF0054) estão implementadas.
- **Análise e recomendação** — RF0055–RF0058, RN0071–RN0074 e RNF0043–RNF0046
  (numeração do DRS; não confundir com RNF0045/RNF0046 da atividade, que
  correspondem às RN0045/RN0046 do DRS).
