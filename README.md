# Nexus — Clientes e Vendas (backend + frontend + testes automatizados)

Dois módulos do Nexus, com testes automatizados de interface (Cypress)
cobrindo os RF/RN/RNF do DRS:

- **Gestão de clientes** — cadastrar, consultar, alterar e inativar
  clientes, mais endereços (RF0026), cartões de crédito (RF0027), troca de
  senha (RF0028) e consulta de transações (RF0025).
- **Fluxo de vendas** — carrinho (RF0031/RF0032), compra e finalização
  (RF0033–RF0038), validação do pagamento (RN0037/RN0038), despacho
  (RF0039) e entrega (RF0040).

## Estrutura

```
nexus-crud/
├── backend/                     # Spring Boot (Java 21)
│   ├── pom.xml
│   └── src/main/java/com/games/
│       ├── cliente/
│       │   ├── domain/
│       │   │   ├── entity/       # Cliente, Endereco, Cartao, LogTransacao (entidades JPA)
│       │   │   └── enums/        # GeneroCliente, StatusCliente, TipoEndereco, Bandeira
│       │   ├── application/     # ClienteService, EnderecoService, CartaoService, AuditoriaService + validation/ + exception/
│       │   └── adapter/
│       │       ├── in/web/      # ClienteController, EnderecoController, CartaoController, AuditoriaController, GlobalExceptionHandler, dto/request/ e dto/response/
│       │       └── out/persistence/ # ClienteRepository, EnderecoRepository, CartaoRepository, LogTransacaoRepository
│       └── vendas/
│           ├── domain/
│           │   ├── entity/       # Jogo, Carrinho, CarrinhoItem, Pedido, PedidoItem, PagamentoCartao, PedidoCupom, Cupom, EnderecoEntrega, ParametroSistema
│           │   └── enums/        # StatusPedido, TipoCupom, SituacaoItemCarrinho
│           ├── application/     # CarrinhoService, PedidoService, CupomService, JogoService, EstoqueService, FreteService, ParametroService, OperadoraCartaoSimulada
│           ├── adapter/
│           │   ├── in/web/      # CarrinhoController, PedidoController, CupomController, JogoController, ParametroController, dto/request/ e dto/response/
│           │   └── out/persistence/ # repositórios Spring Data JPA
│           └── config/          # CargaInicialDominio (RNF0013)
│       resources/                # application.properties, static/ (frontend)
│       test/java/...            # testes unitários (JUnit + Mockito) do ClienteService
├── cypress/
│   ├── e2e/cliente-crud.cy.js          # suíte do módulo de clientes (37 testes)
│   ├── e2e/pedido-caminho-feliz.cy.js  # criação de pedido — suíte da apresentação desta fase (23 testes)
│   ├── e2e/vendas-pos-finalizacao.cy.js # regras de vendas fora do escopo desta fase (13 testes)
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
npx cypress run      # modo headless, roda as duas suítes
npx cypress run --spec cypress/e2e/pedido-caminho-feliz.cy.js   # só a criação de pedido (apresentação)
```

**Velocidade dos testes.** Para a apresentação, os testes rodam com uma pausa
de 200 ms após cada clique, seleção, marcação ou digitação, e digitam mais
devagar (`cypress/support/e2e.js`). Para mudar:

```bash
npx cypress open --env lentidao=1000   # ainda mais lento
npx cypress run --env lentidao=0       # sem pausas (rápido, para conferir tudo de uma vez)
```

O valor padrão fica em `cypress.config.js` (`env.lentidao`).

Na interface, a aba **Loja** é a visão do cliente (escolha o cliente
comprando no topo, já que o módulo não tem login) e a aba **Gestão de
vendas** é a visão do administrador (validar pagamento, despachar,
confirmar entrega, estoque, cupons e parâmetros).

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
| RF0025 | Consulta das transações realizadas pelo cliente | `GET /api/clientes/{id}/pedidos` | *(suíte de vendas)* "a consulta de clientes exibe as transações realizadas pelo cliente" |
| RN0027 | Ranking numérico pelo perfil de compra | `PATCH /api/pedidos/{id}/validar-pagamento` | *(suíte de vendas)* "o ranking do cliente é atualizado conforme o perfil de compra" |

## Criação de pedido — caminho feliz (`pedido-caminho-feliz.cy.js`)

Suíte apresentada nesta fase. Escopo: RF0031–RF0038, RN0023–RN0025,
RN0031, RN0033–RN0036, RNF0011 e RNF0012.

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

## Implementado, fora do escopo desta fase (`vendas-pos-finalizacao.cy.js`)

Regras do DRS já implementadas e testadas, mas que o enunciado da fase de
criação de pedido pede para **não** apresentar.

| RF/RN/RNF | Regra | Teste Cypress |
|---|---|---|
| RN0037 / RN0038 | Validação do pagamento: APROVADA ou REPROVADA (cartão recusado libera os itens e cancela o cupom de troca emitido) | "compra EM PROCESSAMENTO passa a APROVADA…" / "compra com cartão recusado pela operadora fica REPROVADA…" |
| RN0037 | Validade e veracidade dos cupons | "não deve aceitar cupom vencido nem cupom inexistente" |
| RF0039 / RN0039 | Somente compras aprovadas são despachadas (EM TRANSPORTE) | "somente compras aprovadas podem ser despachadas" |
| RF0040 / RN0040 | Confirmação de entrega (ENTREGUE) | "somente compras em transporte têm a entrega confirmada" |
| RN0028 / RF0053 | Baixa no estoque só na aprovação | "a baixa no estoque só acontece quando a compra é aprovada" |
| RN0044 | Itens bloqueados para outros clientes; aviso 5 minutos antes de expirar | "itens no carrinho de um cliente ficam bloqueados…" / "deve notificar o cliente quando faltarem 5 minutos…" |
| RN0044 / RN0045 / RNF0042 | Expiração do bloqueio, itens exibidos como removidos e compra desabilitada | "ao expirar o prazo, os itens são liberados…" |
| RN0032 | Estoque alterado entre o carrinho e a compra | os dois testes "RN0032 — …" |
| RF0025 / RN0027 | Transações e ranking do cliente | "a consulta de clientes exibe as transações…" / "o ranking do cliente é atualizado…" |
| RNF0013 | Carga de domínio na implantação (parâmetros, jogos, cupons promocionais) | `CargaInicialDominio` roda a cada subida e só insere o que falta |

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
- **Cupons de troca para a demonstração** — como a geração por troca está
  fora do escopo, eles são carregados previamente pela Gestão de vendas
  (cadastro de cupom do tipo Troca) ou por `POST /api/cupons`.
- **Operadora de cartão (RN0037)** — simulada: recusa cartões cujo número
  termina em `0000` e aprova os demais (`OperadoraCartaoSimulada`). A
  validação é disparada pelo administrador em "Validar pagamento".
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
carrinho, compra) e com `admin` nas ações da Gestão de vendas (validar
pagamento, despachar, entregar, estoque, cupons, parâmetros). Em um
sistema com login real, este valor viria do usuário autenticado na
sessão. A auditoria cobre inserções e alterações (INSERT/UPDATE),
conforme o texto literal do requisito; remoções não geram log.

## Fora do escopo desta entrega

- **Fluxo de troca** — RF0041–RF0045, RN0041–RN0043 e RN0046 (os cupons
  de troca já são aceitos no pagamento; a geração a partir de uma troca
  pertence a esse fluxo).
- **Cadastro de jogos** — RF0011–RF0016 e RN0011–RN0017 (no DRS, "livros"). Existe apenas
  um cadastro simplificado (`POST /api/jogos`) para alimentar a loja.
- **Controle de estoque** — RF0051/RF0052/RF0054 e RN0050–RN0062. Existe
  apenas o ajuste manual de estoque na Gestão de vendas; a baixa por venda
  (RF0053) está implementada.
- **Análise e recomendação** — RF0055–RF0058, RN0071–RN0074 e RNF0043–RNF0046.
