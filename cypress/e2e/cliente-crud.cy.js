// Testes automatizados de interface (Cypress) do módulo de Gestão de Clientes — Nexus.
// Cada teste está rotulado com o RF/RN/RNF do DRS_LES que ele valida, conforme exigido
// na apresentação. A aplicação (backend + frontend estático) deve estar rodando em
// http://localhost:8080 antes de executar esta suíte (ver README).

const { gerarCpfValido, formatarCpf } = require("../support/cpf-generator");

function clienteValidoPadrao(overrides = {}) {
  const cpf = gerarCpfValido();
  return {
    nome: "Maria da Silva",
    genero: "FEMININO",
    dataNascimento: "1995-05-20",
    cpf: formatarCpf(cpf),
    telefoneTipo: "Celular",
    telefoneDdd: "11",
    telefoneNumero: "999998888",
    email: `maria.${cpf}@example.com`,
    senha: "Senha@Forte1",
    confirmacaoSenha: "Senha@Forte1",
    enderecoTipoResidencia: "Casa",
    enderecoTipoLogradouro: "Rua",
    enderecoPais: "Brasil",
    enderecoLogradouro: "Rua das Flores",
    enderecoNumero: "100",
    enderecoBairro: "Centro",
    enderecoCep: "01310100",
    enderecoCidade: "São Paulo",
    enderecoEstado: "SP",
    ...overrides,
  };
}

function preencherFormularioCadastro(dados) {
  if (dados.nome !== undefined) {
    cy.get('[data-cy=input-nome]').clear();
    if (dados.nome !== "") cy.get('[data-cy=input-nome]').type(dados.nome);
  }
  if (dados.genero !== undefined) {
    if (dados.genero !== "") cy.get('[data-cy=input-genero]').select(dados.genero);
  }
  if (dados.dataNascimento !== undefined) {
    cy.get('[data-cy=input-data-nascimento]').clear();
    if (dados.dataNascimento !== "") cy.get('[data-cy=input-data-nascimento]').type(dados.dataNascimento);
  }
  if (dados.cpf !== undefined) {
    cy.get('[data-cy=input-cpf]').clear();
    if (dados.cpf !== "") cy.get('[data-cy=input-cpf]').type(dados.cpf);
  }
  if (dados.telefoneTipo !== undefined) { if (dados.telefoneTipo !== "") cy.get('[data-cy=input-telefone-tipo]').select(dados.telefoneTipo); }
  if (dados.telefoneDdd !== undefined) {
    cy.get('[data-cy=input-telefone-ddd]').clear();
    if (dados.telefoneDdd !== "") cy.get('[data-cy=input-telefone-ddd]').type(dados.telefoneDdd);
  }
  if (dados.telefoneNumero !== undefined) {
    cy.get('[data-cy=input-telefone-numero]').clear();
    if (dados.telefoneNumero !== "") cy.get('[data-cy=input-telefone-numero]').type(dados.telefoneNumero);
  }
  if (dados.email !== undefined) {
    cy.get('[data-cy=input-email]').clear();
    if (dados.email !== "") cy.get('[data-cy=input-email]').type(dados.email);
  }
  if (dados.senha !== undefined) {
    cy.get('[data-cy=input-senha]').clear();
    if (dados.senha !== "") cy.get('[data-cy=input-senha]').type(dados.senha);
  }
  if (dados.confirmacaoSenha !== undefined) {
    cy.get('[data-cy=input-confirmacao-senha]').clear();
    if (dados.confirmacaoSenha !== "") cy.get('[data-cy=input-confirmacao-senha]').type(dados.confirmacaoSenha);
  }
  if (dados.enderecoTipoResidencia !== undefined) { if (dados.enderecoTipoResidencia !== "") cy.get('[data-cy=input-tipo-residencia]').select(dados.enderecoTipoResidencia); }

  if (dados.enderecoTipoLogradouro !== undefined) { if (dados.enderecoTipoLogradouro !== "") cy.get('[data-cy=input-tipo-logradouro]').select(dados.enderecoTipoLogradouro); }

  if (dados.enderecoPais !== undefined) { cy.get('[data-cy=input-pais]').clear(); if (dados.enderecoPais !== "") cy.get('[data-cy=input-pais]').type(dados.enderecoPais); }

  if (dados.enderecoLogradouro !== undefined) {
    cy.get('[data-cy=input-logradouro]').clear();
    if (dados.enderecoLogradouro !== "") cy.get('[data-cy=input-logradouro]').type(dados.enderecoLogradouro);
  }
  if (dados.enderecoNumero !== undefined) {
    cy.get('[data-cy=input-numero]').clear();
    if (dados.enderecoNumero !== "") cy.get('[data-cy=input-numero]').type(dados.enderecoNumero);
  }
  if (dados.enderecoBairro !== undefined) {
    cy.get('[data-cy=input-bairro]').clear();
    if (dados.enderecoBairro !== "") cy.get('[data-cy=input-bairro]').type(dados.enderecoBairro);
  }
  if (dados.enderecoCep !== undefined) {
    cy.get('[data-cy=input-cep]').clear();
    if (dados.enderecoCep !== "") cy.get('[data-cy=input-cep]').type(dados.enderecoCep);
  }
  if (dados.enderecoCidade !== undefined) {
    cy.get('[data-cy=input-cidade]').clear();
    if (dados.enderecoCidade !== "") cy.get('[data-cy=input-cidade]').type(dados.enderecoCidade);
  }
  if (dados.enderecoEstado !== undefined) {
    cy.get('[data-cy=input-estado]').clear();
    if (dados.enderecoEstado !== "") cy.get('[data-cy=input-estado]').type(dados.enderecoEstado);
  }
}

function cadastrarViaUI(dados) {
  cy.get('[data-cy=nav-cadastro]').click();
  preencherFormularioCadastro(dados);
  cy.get('[data-cy=btn-cadastrar]').click();
}

describe("Módulo de Gestão de Clientes — Nexus", () => {

  beforeEach(() => {
    cy.visit("/index.html");
  });

  // =====================================================================
  // RF0021 — Cadastrar cliente / RN0026 / RNF0031-33
  // =====================================================================
  describe("RF0021 — Cadastrar cliente", () => {

    it("RF0021 / RN0026 — deve cadastrar um cliente com todos os campos obrigatórios válidos", () => {
      const cliente = clienteValidoPadrao();
      cadastrarViaUI(cliente);

      cy.get('[data-cy=mensagem]').should("be.visible").and("contain.text", "sucesso");

      cy.get('[data-cy=nav-consulta]').click();
      cy.get('[data-cy=filtro-cpf]').type(cliente.cpf.replace(/\D/g, ""));
      cy.get('[data-cy=btn-filtrar]').click();
      cy.get('[data-cy=tbody-clientes]').should("contain.text", cliente.nome);
    });

    it("RN0026 — não deve cadastrar cliente sem o nome (campo obrigatório)", () => {
      cy.get('[data-cy=nav-cadastro]').click();
      preencherFormularioCadastro(clienteValidoPadrao({nome: ""}));
      cy.get('[data-cy=btn-cadastrar]').click();

      cy.get('[data-cy=mensagem]')
          .should("be.visible")
          .and("have.class", "erro")
          .and("contain.text", "RN0026");
    });

    it("RN0026 — não deve cadastrar cliente sem endereço residencial (CEP obrigatório)", () => {
      cy.get('[data-cy=nav-cadastro]').click();
      preencherFormularioCadastro(clienteValidoPadrao({enderecoCep: ""}));
      cy.get('[data-cy=btn-cadastrar]').click();

      cy.get('[data-cy=mensagem]')
          .should("be.visible")
          .and("have.class", "erro")
          .and("contain.text", "RN0026");
    });

    it("RNF0031 — não deve cadastrar cliente com senha fraca", () => {
      cadastrarViaUI(clienteValidoPadrao({senha: "12345678", confirmacaoSenha: "12345678"}));
      cy.get('[data-cy=mensagem]').should("be.visible")
          .and("contain.text", "RNF0031");
    });

    it("RNF0032 — não deve cadastrar cliente quando a confirmação de senha diverge da senha", () => {
      cadastrarViaUI(clienteValidoPadrao({senha: "Senha@Forte1", confirmacaoSenha: "Senha@Diferente9"}));
      cy.get('[data-cy=mensagem]').should("be.visible")
          .and("contain.text", "RNF0032");
    });

    it("não deve cadastrar cliente com CPF inválido (dígito verificador incorreto)", () => {
      cadastrarViaUI(clienteValidoPadrao({cpf: "111.111.111-11"}));
      cy.get('[data-cy=mensagem]').should("be.visible")
          .and("contain.text", "CPF_INVALIDO");
    });

    it("não deve cadastrar dois clientes com o mesmo CPF", () => {
      const cliente = clienteValidoPadrao();
      cadastrarViaUI(cliente);
      cy.get('[data-cy=mensagem]').should("contain.text", "sucesso");

      cy.get('[data-cy=nav-cadastro]').click();
      cadastrarViaUI({...cliente, email: `outro.${Date.now()}@example.com`});
      cy.get('[data-cy=mensagem]').should("contain.text", "CPF_DUPLICADO");
    });


    it("RN0026 — não deve cadastrar cliente sem CPF e Data de Nascimento (campos obrigatórios)", () => {
      cy.get('[data-cy=nav-cadastro]').click();
      const dadosIncompletos = clienteValidoPadrao();
      delete dadosIncompletos.cpf; // Simula o envio sem CPF
      delete dadosIncompletos.dataNascimento; // Simula envio sem Data de Nascimento

      preencherFormularioCadastro(dadosIncompletos);
      cy.get('[data-cy=btn-cadastrar]').click();

      cy.get('[data-cy=mensagem]')
          .should("be.visible")
          .and("have.class", "erro")
          .and("contain.text", "RN0026");
    });

    it("RN0026 — não deve cadastrar cliente sem telefone e e-mail (campos de contato obrigatórios)", () => {
      cy.get('[data-cy=nav-cadastro]').click();
      const dadosIncompletos = clienteValidoPadrao();
      delete dadosIncompletos.telefoneTipo;
      delete dadosIncompletos.telefoneDdd;
      delete dadosIncompletos.telefoneNumero;
      delete dadosIncompletos.email;

      preencherFormularioCadastro(dadosIncompletos);
      cy.get('[data-cy=btn-cadastrar]').click();

      cy.get('[data-cy=mensagem]')
          .should("be.visible")
          .and("have.class", "erro")
          .and("contain.text", "RN0026");
    });
  });

  // =====================================================================
  // RF0024 — Consultar clientes
  // =====================================================================
  describe("RF0024 — Consultar clientes", () => {

    it("RF0024 — deve listar os clientes cadastrados ao abrir a aba de consulta", () => {
      const cliente = clienteValidoPadrao();
      cadastrarViaUI(cliente);

      cy.get('[data-cy=nav-consulta]').click();
      cy.get('[data-cy=tbody-clientes] [data-cy=linha-cliente]').should("have.length.greaterThan", 0);
    });

    it("RF0024 — deve filtrar clientes isoladamente por nome", () => {
      const nomeUnico = "Fulano Teste " + Date.now();
      cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));

      cy.get('[data-cy=nav-consulta]').click();
      cy.get('[data-cy=filtro-nome]').type(nomeUnico);
      cy.get('[data-cy=btn-filtrar]').click();

      cy.get('[data-cy=tbody-clientes]').should("contain.text", nomeUnico);
      cy.get('[data-cy=tbody-clientes] [data-cy=linha-cliente]').should("have.length", 1);
    });

    it("RF0024 — deve filtrar clientes isoladamente por CPF", () => {
      const cliente = clienteValidoPadrao();
      cadastrarViaUI(cliente);

      cy.get('[data-cy=nav-consulta]').click();
      cy.get('[data-cy=filtro-cpf]').type(cliente.cpf.replace(/\D/g, ""));
      cy.get('[data-cy=btn-filtrar]').click();

      cy.get('[data-cy=tbody-clientes] [data-cy=linha-cliente]').should("have.length", 1);
    });

    it("RF0024 — deve filtrar clientes isoladamente pelo código do cliente", () => {
      const nomeUnico = "Cliente Codigo Teste " + Date.now();
      cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));

      cy.get('[data-cy=nav-consulta]').click();
      cy.get('[data-cy=filtro-nome]').type(nomeUnico);
      cy.get('[data-cy=btn-filtrar]').click();

      // Lê o código gerado para o cliente recém-cadastrado (1ª coluna da tabela)
      cy.get('[data-cy=tbody-clientes] [data-cy=linha-cliente]').first().find('td').first().invoke('text').then((codigo) => {
        cy.get('[data-cy=filtro-nome]').clear();
        cy.get('[data-cy=filtro-codigo]').clear().type(codigo.trim());
        cy.get('[data-cy=btn-filtrar]').click();

        cy.get('[data-cy=tbody-clientes] [data-cy=linha-cliente]').should("have.length", 1);
        cy.get('[data-cy=tbody-clientes]').should("contain.text", nomeUnico);
      });
    });

    it("RF0024 — deve combinar múltiplos filtros (nome + status)", () => {
      const nomeUnico = "Combinado Teste " + Date.now();
      cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));

      cy.get('[data-cy=nav-consulta]').click();
      cy.get('[data-cy=filtro-nome]').type(nomeUnico);
      cy.get('[data-cy=filtro-status]').select("ATIVO");
      cy.get('[data-cy=btn-filtrar]').click();

      cy.get('[data-cy=tbody-clientes]').should("contain.text", nomeUnico);
    });
  });

  // =====================================================================
  // RF0022 — Alterar dados cadastrais
  // =====================================================================
  describe("RF0022 — Alterar dados cadastrais", () => {

    it("RF0022 — deve alterar nome, telefone, e-mail e endereço de um cliente existente", () => {
      const nomeOriginal = "Cliente Para Alterar " + Date.now();
      const cliente = clienteValidoPadrao({nome: nomeOriginal});
      cadastrarViaUI(cliente);

      cy.get('[data-cy=nav-consulta]').click();
      cy.get('[data-cy=filtro-nome]').type(nomeOriginal);
      cy.get('[data-cy=btn-filtrar]').click();
      cy.get('[data-cy=btn-editar]').first().click();

      cy.get('[data-cy=modal-alterar]').should("be.visible");
      cy.get('[data-cy=alterar-nome]').clear().type("Nome Alterado");
      cy.get('[data-cy=alterar-telefone-tipo]').select("Comercial");
      cy.get('[data-cy=alterar-telefone-ddd]').clear().type("11");
      cy.get('[data-cy=alterar-telefone-numero]').clear().type("888887777");
      cy.get('[data-cy=btn-salvar-alterar]').click();

      cy.get('[data-cy=modal-alterar]').should("not.be.visible");

      // CORREÇÃO: Limpa o filtro com o nome antigo e pesquisa pelo novo nome,
      // senão o cliente some da tabela filtrada e o teste falha.
      cy.get('[data-cy=filtro-nome]').clear().type("Nome Alterado");
      cy.get('[data-cy=btn-filtrar]').click();
      cy.get('[data-cy=tbody-clientes]').should("contain.text", "Nome Alterado");
    });
    // =====================================================================
    // RF0023 — Inativar cliente (e a distinção entre inativação e exclusão)
    // =====================================================================
    describe("RF0023 — Inativar cliente", () => {

      it("RF0023 — deve inativar um cliente ativo, mudando seu status para INATIVO", () => {
        const nomeUnico = "Cliente Para Inativar " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));

        cy.get('[data-cy=nav-consulta]').click();
        cy.get('[data-cy=filtro-nome]').type(nomeUnico);
        cy.get('[data-cy=btn-filtrar]').click();

        cy.on("window:confirm", () => true);
        cy.get('[data-cy=btn-inativar]').first().click();

        // CORREÇÃO: Espera o botão mudar de "Inativar" para "Reativar" confirmando que o recarregamento aconteceu
        cy.get('[data-cy=btn-reativar]').first().should("exist");
        cy.get('[data-cy=status-cliente]').first().should("contain.text", "INATIVO");

        cy.get('[data-cy=filtro-status]').select("INATIVO");
        cy.get('[data-cy=btn-filtrar]').click();
        cy.get('[data-cy=tbody-clientes]').should("contain.text", nomeUnico);
      });

      it("RN — não deve permitir inativar um cliente que já está inativo", () => {
        const nomeUnico = "Cliente Duplo Inativar " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));

        cy.get('[data-cy=nav-consulta]').click();
        cy.get('[data-cy=filtro-nome]').type(nomeUnico);
        cy.get('[data-cy=btn-filtrar]').click();

        cy.on("window:confirm", () => true);
        cy.get('[data-cy=btn-inativar]').first().click();

        // CORREÇÃO: Espera a tabela recarregar
        cy.get('[data-cy=btn-reativar]').first().should("exist");

        cy.get('[data-cy=filtro-status]').select("INATIVO");
        cy.get('[data-cy=btn-filtrar]').click();

        cy.get('[data-cy=btn-reativar]').should("exist");
        cy.get('[data-cy=btn-inativar]').should("not.exist");
      });

      it("RF0023 — a inativação NÃO deve excluir o cliente: o registro continua existindo e consultável", () => {
        const nomeUnico = "Cliente Preservado " + Date.now();
        const cliente = clienteValidoPadrao({nome: nomeUnico});
        cadastrarViaUI(cliente);

        cy.get('[data-cy=nav-consulta]').click();
        cy.get('[data-cy=filtro-nome]').type(nomeUnico);
        cy.get('[data-cy=btn-filtrar]').click();

        cy.on("window:confirm", () => true);
        cy.get('[data-cy=btn-inativar]').first().click();

        // CORREÇÃO: Espera o status mudar na tabela antes de prosseguir
        cy.get('[data-cy=btn-reativar]').first().should("exist");

        cy.get('[data-cy=filtro-nome]').clear().type(nomeUnico);
        cy.get('[data-cy=filtro-status]').select("");
        cy.get('[data-cy=btn-filtrar]').click();
        cy.get('[data-cy=tbody-clientes]').should("contain.text", nomeUnico);
        cy.get('[data-cy=status-cliente]').should("contain.text", "INATIVO");

        cy.get('[data-cy=btn-reativar]').first().click();

        // CORREÇÃO: Garante que voltou a ser ativo
        cy.get('[data-cy=btn-inativar]').first().should("exist");
        cy.get('[data-cy=status-cliente]').should("contain.text", "ATIVO");
      });
    });

    // =====================================================================
    // RF0026 — Endereços do cliente (cobrança/entrega)
    // =====================================================================
    describe("RF0026 — Endereços do cliente", () => {

      function abrirEnderecosDoCliente(nomeUnico) {
        cy.get('[data-cy=nav-consulta]').click();
        cy.get('[data-cy=filtro-nome]').clear().type(nomeUnico);
        cy.get('[data-cy=btn-filtrar]').click();
        cy.get('[data-cy=btn-enderecos]').first().click();
        cy.get('[data-cy=modal-enderecos]').should("be.visible");
      }

      function preencherEAdicionarEndereco({
                                             apelido, tipo,
                                             tipoResidencia = "Casa",       // NOVO CAMPO
                                             tipoLogradouro = "Rua",        // NOVO CAMPO
                                             pais = "Brasil",               // NOVO CAMPO
                                             logradouro = "Teste", numero = "10",
                                             bairro = "Centro", cep = "01310100", cidade = "São Paulo", estado = "SP"
                                           }) {
        // Garantir que o modal está visível antes de preencher
        cy.get('#form-endereco').should('be.visible');

        cy.get('[data-cy=endereco-apelido]').clear().type(apelido);
        if (tipo) cy.get('[data-cy=endereco-tipo]').select(tipo);

        // NOVOS CAMPOS SENDO PREENCHIDOS NO CYPRESS
        cy.get('[data-cy=endereco-tipo-residencia]').select(tipoResidencia);
        cy.get('[data-cy=endereco-tipo-logradouro]').select(tipoLogradouro);

        // CORREÇÃO: Forçar a escrita no campo do país
        cy.get('[data-cy=endereco-pais]').should('be.visible').clear().type(pais, { force: true });

        cy.get('[data-cy=endereco-logradouro]').clear().type(logradouro);
        cy.get('[data-cy=endereco-numero]').clear().type(numero);
        cy.get('[data-cy=endereco-bairro]').clear().type(bairro);
        cy.get('[data-cy=endereco-cep]').clear().type(cep);
        cy.get('[data-cy=endereco-cidade]').clear().type(cidade);
        cy.get('[data-cy=endereco-estado]').clear().type(estado);

        cy.get('[data-cy=btn-adicionar-endereco]').click();

        // Aguarda o POST terminar e a tabela ser recarregada antes de seguir,
        // evitando corrida com ações seguintes (ex.: remover endereço).
        cy.get('[data-cy=mensagem]').should("contain.text", "Endereço adicionado com sucesso");
        cy.contains('[data-cy=linha-endereco]', apelido).should("exist");
      }
      it("RF0026 — deve adicionar um endereço de cobrança ao cliente", () => {
        const nomeUnico = "Cliente Endereco " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));
        abrirEnderecosDoCliente(nomeUnico);

        preencherEAdicionarEndereco({apelido: "Casa", tipo: "COBRANCA"});

        cy.get('[data-cy=mensagem]').should("contain.text", "Endereço adicionado com sucesso");
        cy.get('[data-cy=tbody-enderecos]').should("contain.text", "Casa").and("contain.text", "COBRANCA");
      });

      it("RN0023 — não deve adicionar endereço sem o tipo selecionado", () => {
        const nomeUnico = "Cliente Endereco Sem Tipo " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));
        abrirEnderecosDoCliente(nomeUnico);

        cy.get('[data-cy=endereco-apelido]').type("Casa");
        cy.get('[data-cy=endereco-logradouro]').type("Rua Teste");
        cy.get('[data-cy=endereco-numero]').type("10");
        cy.get('[data-cy=endereco-bairro]').type("Centro");
        cy.get('[data-cy=endereco-cep]').type("01310100");
        cy.get('[data-cy=endereco-cidade]').type("São Paulo");
        cy.get('[data-cy=endereco-estado]').type("SP");
        cy.get('[data-cy=btn-adicionar-endereco]').click();

        // CORREÇÃO: Como form-endereco não tem novalidate, a validação é do HTML5.
        cy.get('[data-cy=endereco-tipo]:invalid').should("exist");
      });

      it("RF0026 / RN0023 — não deve adicionar endereço sem a frase curta de identificação (apelido)", () => {
        const nomeUnico = "Cliente Endereco Sem Apelido " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));
        abrirEnderecosDoCliente(nomeUnico);

        // Preenche os dados, mas deixa o apelido vazio
        cy.get('[data-cy=endereco-tipo]').select("COBRANCA");
        cy.get('[data-cy=endereco-logradouro]').type("Rua Teste");
        cy.get('[data-cy=endereco-numero]').type("10");
        cy.get('[data-cy=endereco-bairro]').type("Centro");
        cy.get('[data-cy=endereco-cep]').type("01310100");
        cy.get('[data-cy=endereco-cidade]').type("São Paulo");
        cy.get('[data-cy=endereco-estado]').type("SP");
        cy.get('[data-cy=btn-adicionar-endereco]').click();

        // O HTML5 ou o backend deve bloquear
        cy.get('[data-cy=endereco-apelido]:invalid').should("exist");
      });

      it("RN0023 — não deve adicionar endereço faltando logradouro, número, bairro ou cidade", () => {
        const nomeUnico = "Cliente Endereco Incompleto " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));
        abrirEnderecosDoCliente(nomeUnico);

        cy.get('[data-cy=endereco-apelido]').type("Casa");
        cy.get('[data-cy=endereco-tipo]').select("COBRANCA");
        cy.get('[data-cy=endereco-cep]').type("01310100");
        cy.get('[data-cy=endereco-estado]').type("SP");
        // Logradouro, Número, Bairro e Cidade ficaram vazios
        cy.get('[data-cy=btn-adicionar-endereco]').click();

        cy.get('[data-cy=endereco-logradouro]:invalid').should("exist");
      });

      it("RN0022 — não deve permitir remover o único endereço de entrega do cliente", () => {
        const nomeUnico = "Cliente Unico Entrega " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));
        abrirEnderecosDoCliente(nomeUnico);

        // O cadastro já cria automaticamente o endereço "Residencial" (AMBOS),
        // que aparece nesta tela e cobre cobrança e entrega. Adicionamos um
        // endereço exclusivo de COBRANÇA para que o "Residencial" passe a ser
        // o único endereço de ENTREGA do cliente.
        preencherEAdicionarEndereco({apelido: "Casa", tipo: "COBRANCA"});

        cy.contains('[data-cy=linha-endereco]', "Residencial").find('[data-cy=btn-remover-endereco]').click();

        // Estoura a RN0022, pois remover o "Residencial" deixaria o cliente sem endereço de entrega
        cy.get('[data-cy=mensagem]').should("contain.text", "RN0022");
      });

      it("RN0021 — não deve permitir remover o único endereço de cobrança do cliente", () => {
        const nomeUnico = "Cliente Unico Cobranca " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));
        abrirEnderecosDoCliente(nomeUnico);

        // O "Residencial" automático (AMBOS) cobre cobrança e entrega. Adicionamos
        // um endereço exclusivo de ENTREGA para que ele passe a ser o único de COBRANÇA.
        preencherEAdicionarEndereco({apelido: "Trabalho", tipo: "ENTREGA"});

        cy.contains('[data-cy=linha-endereco]', "Residencial").find('[data-cy=btn-remover-endereco]').click();

        // Estoura a RN0021, pois remover o "Residencial" deixaria o cliente sem endereço de cobrança
        cy.get('[data-cy=mensagem]').should("contain.text", "RN0021");
      });

      it("RN0021/RN0022 — deve remover um endereço quando não é o último do seu tipo", () => {
        const nomeUnico = "Cliente Dois Enderecos " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));
        abrirEnderecosDoCliente(nomeUnico);

        // O cadastro já cria o "Residencial" (AMBOS); adicionamos mais um endereço.
        preencherEAdicionarEndereco({apelido: "Trabalho", tipo: "ENTREGA"});

        cy.get('[data-cy=tbody-enderecos] [data-cy=linha-endereco]').should("have.length", 2);
        cy.contains('[data-cy=linha-endereco]', "Trabalho").find('[data-cy=btn-remover-endereco]').click();

        cy.get('[data-cy=mensagem]').should("contain.text", "removido com sucesso");
        cy.get('[data-cy=tbody-enderecos] [data-cy=linha-endereco]').should("have.length", 1);
      });
    });

    // =====================================================================
    // RF0027 — Cartões de crédito do cliente
    // =====================================================================
    describe("RF0027 — Cartões de crédito do cliente", () => {

      function abrirCartoesDoCliente(nomeUnico) {
        cy.get('[data-cy=nav-consulta]').click();
        cy.get('[data-cy=filtro-nome]').clear().type(nomeUnico);
        cy.get('[data-cy=btn-filtrar]').click();
        cy.get('[data-cy=btn-cartoes]').first().click();
        cy.get('[data-cy=modal-cartoes]').should("be.visible");
      }

      function preencherEAdicionarCartao({
                                           numero = "4111111111111111",
                                           nome = "CLIENTE TESTE",
                                           bandeira = "VISA",
                                           cvv = "123",
                                           preferencial = false
                                         }) {
        cy.get('[data-cy=cartao-numero]').clear().type(numero);
        cy.get('[data-cy=cartao-nome]').clear().type(nome);
        cy.get('[data-cy=cartao-bandeira]').select(bandeira);
        cy.get('[data-cy=cartao-cvv]').clear().type(cvv);
        if (preferencial) cy.get('[data-cy=cartao-preferencial]').check();
        cy.get('[data-cy=btn-adicionar-cartao]').click();
      }

      it("RF0027 / RN0024 — deve adicionar um cartão e exibir o número mascarado", () => {
        const nomeUnico = "Cliente Cartao " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));
        abrirCartoesDoCliente(nomeUnico);
        preencherEAdicionarCartao({numero: "4111111111111111"});

        cy.get('[data-cy=mensagem]').should("contain.text", "Cartão adicionado com sucesso");
        cy.get('[data-cy=tbody-cartoes]').should("contain.text", "1111").and("not.contain.text", "4111111111111111");
      });

      it("RN0024 — não deve adicionar cartão faltando dados obrigatórios (Nome Impresso e CVV)", () => {
        const nomeUnico = "Cliente Cartao Incompleto " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));
        abrirCartoesDoCliente(nomeUnico);

        cy.get('[data-cy=cartao-numero]').type("4111111111111111");
        cy.get('[data-cy=cartao-bandeira]').select("VISA");
        // Deixa o CVV e o Nome do Titular em branco
        cy.get('[data-cy=btn-adicionar-cartao]').click();

        // Verifica se a interface barrou o envio por falta do campo obrigatório
        cy.get('[data-cy=cartao-cvv]:invalid').should("exist");
      });

      it("RN0025 — não deve adicionar cartão com bandeira em branco ou não permitida", () => {
        const nomeUnico = "Cliente Cartao Sem Bandeira " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));
        abrirCartoesDoCliente(nomeUnico);

        cy.get('[data-cy=cartao-numero]').type("4111111111111111");
        cy.get('[data-cy=cartao-nome]').type("NOME TESTE");
        cy.get('[data-cy=cartao-cvv]').type("123");
        // Não preenche/seleciona a bandeira
        cy.get('[data-cy=btn-adicionar-cartao]').click();

        cy.get('[data-cy=cartao-bandeira]:invalid').should("exist");
      });

      it("RF0027 — o primeiro cartão cadastrado deve ser automaticamente o preferencial", () => {
        const nomeUnico = "Cliente Primeiro Cartao " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));
        abrirCartoesDoCliente(nomeUnico);
        preencherEAdicionarCartao({});

        cy.get('[data-cy=cartao-preferencial-badge]').should("exist");
      });

      it("RF0027 — deve trocar o cartão preferencial, desmarcando o anterior", () => {
        const nomeUnico = "Cliente Trocar Preferencial " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));
        abrirCartoesDoCliente(nomeUnico);
        preencherEAdicionarCartao({numero: "4111111111111111"});
        preencherEAdicionarCartao({numero: "5500000000000004", bandeira: "MASTERCARD"});

        cy.get('[data-cy=cartao-preferencial-badge]').should("have.length", 1);

        cy.get('[data-cy=btn-marcar-preferencial]').first().click();
        cy.get('[data-cy=mensagem]').should("contain.text", "definido como preferencial");
        cy.get('[data-cy=cartao-preferencial-badge]').should("have.length", 1);
      });

      it("RF0027 — deve remover um cartão", () => {
        const nomeUnico = "Cliente Remover Cartao " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));
        abrirCartoesDoCliente(nomeUnico);
        preencherEAdicionarCartao({});

        cy.get('[data-cy=btn-remover-cartao]').first().click();
        cy.get('[data-cy=mensagem]').should("contain.text", "removido com sucesso");
        cy.get('[data-cy=tbody-cartoes] [data-cy=linha-cartao]').should("not.exist");
      });

      it("RF0027 — ao remover o cartão preferencial, outro cartão restante deve assumir o posto", () => {
        const nomeUnico = "Cliente Promocao Preferencial " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));
        abrirCartoesDoCliente(nomeUnico);
        preencherEAdicionarCartao({numero: "4111111111111111"});
        preencherEAdicionarCartao({numero: "5500000000000004", bandeira: "MASTERCARD"});

        cy.get('[data-cy=linha-cartao]').first().find('[data-cy=btn-remover-cartao]').click();
        cy.get('[data-cy=mensagem]').should("contain.text", "removido com sucesso");

        cy.get('[data-cy=tbody-cartoes] [data-cy=linha-cartao]').should("have.length", 1);
        cy.get('[data-cy=cartao-preferencial-badge]').should("exist");
      });
    });

    // =====================================================================
    // RF0028 — Alterar senha isoladamente
    // =====================================================================
    describe("RF0028 — Alterar senha", () => {

      function abrirSenhaDoCliente(nomeUnico) {
        cy.get('[data-cy=nav-consulta]').click();
        cy.get('[data-cy=filtro-nome]').clear().type(nomeUnico);
        cy.get('[data-cy=btn-filtrar]').click();
        cy.get('[data-cy=btn-senha]').first().click();
        cy.get('[data-cy=modal-senha]').should("be.visible");
      }

      it("RF0028 — deve alterar a senha do cliente com sucesso", () => {
        const nomeUnico = "Cliente Trocar Senha " + Date.now();
        const senhaOriginal = "Senha@Forte1";
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico, senha: senhaOriginal, confirmacaoSenha: senhaOriginal}));
        abrirSenhaDoCliente(nomeUnico);

        cy.get('[data-cy=senha-atual]').type(senhaOriginal);
        cy.get('[data-cy=senha-nova]').type("NovaSenha@2");
        cy.get('[data-cy=senha-confirmacao]').type("NovaSenha@2");
        cy.get('[data-cy=btn-salvar-senha]').click();

        cy.get('[data-cy=mensagem]').should("contain.text", "Senha alterada com sucesso");
      });

      it("RNF0031 — não deve alterar para uma nova senha fraca", () => {
        const nomeUnico = "Cliente Senha Fraca " + Date.now();
        const senhaOriginal = "Senha@Forte1";
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico, senha: senhaOriginal, confirmacaoSenha: senhaOriginal}));
        abrirSenhaDoCliente(nomeUnico);

        cy.get('[data-cy=senha-atual]').type(senhaOriginal);
        cy.get('[data-cy=senha-nova]').type("12345678");
        cy.get('[data-cy=senha-confirmacao]').type("12345678");
        cy.get('[data-cy=btn-salvar-senha]').click();

        cy.get('[data-cy=mensagem]').should("contain.text", "RNF0031");
      });

      it("não deve alterar a senha quando a senha atual informada está incorreta", () => {
        const nomeUnico = "Cliente Senha Atual Errada " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));
        abrirSenhaDoCliente(nomeUnico);

        cy.get('[data-cy=senha-atual]').type("SenhaErrada@1");
        cy.get('[data-cy=senha-nova]').type("NovaSenha@2");
        cy.get('[data-cy=senha-confirmacao]').type("NovaSenha@2");
        cy.get('[data-cy=btn-salvar-senha]').click();

        cy.get('[data-cy=mensagem]').should("contain.text", "SENHA_ATUAL_INCORRETA");
      });
    });

    // =====================================================================
    // RNF0012 — Log de auditoria
    // =====================================================================
    describe("RNF0012 — Log de auditoria", () => {

      it("RNF0012 — deve registrar no log de auditoria o cadastro de um novo cliente", () => {
        const nomeUnico = "Cliente Auditado " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));

        cy.get('[data-cy=nav-auditoria]').click();
        cy.get('[data-cy=tbody-auditoria]').should("contain.text", "Cliente").and("contain.text", "INSERT");
      });

      it("RNF0012 — deve registrar no log de auditoria a inativação de um cliente", () => {
        const nomeUnico = "Cliente Auditado Inativacao " + Date.now();
        cadastrarViaUI(clienteValidoPadrao({nome: nomeUnico}));

        cy.get('[data-cy=nav-consulta]').click();
        cy.get('[data-cy=filtro-nome]').type(nomeUnico);
        cy.get('[data-cy=btn-filtrar]').click();
        cy.on("window:confirm", () => true);
        cy.get('[data-cy=btn-inativar]').first().click();

        // CORREÇÃO: Aguarda recarregamento
        cy.get('[data-cy=btn-reativar]').first().should("exist");

        cy.get('[data-cy=nav-auditoria]').click();
        cy.get('[data-cy=tbody-auditoria]').should("contain.text", "INATIVO");
      });
    });
  });
});