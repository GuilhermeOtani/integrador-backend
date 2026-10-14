# Documentação do Projeto Integrador 2026

## 1. Escopo desta documentação

Este documento descreve o estado efetivamente implementado no backend em **14 de outubro de 2026**. As informações abaixo foram conferidas diretamente nos controllers, services, repositories, entidades, DTOs, enums, configurações, `pom.xml` e testes.

O projeto está em desenvolvimento. Por isso, o documento diferencia recursos expostos pela API REST, recursos apenas internos, regras de negócio implementadas e limitações presentes no código atual.

---

## 2. Visão geral

O Projeto Integrador é um backend para gestão de transporte escolar/universitário. A aplicação usa **Java 21**, **Spring Boot 4.0.3**, Spring Web MVC, Spring Data JPA, Spring Security, PostgreSQL e Lombok.

No estado atual, a API possui operações para alunos, motoristas, ônibus, faculdades, rotas, pontos de embarque, viagens, grades diárias, contas a pagar, pagamentos, contas a receber, recebimentos, recibos e chamados de manutenção. Também possui autenticação JWT, consulta do perfil autenticado, administração de contas de acesso, dashboards específicos por papel e integração individual com a WhatsApp Cloud API para programação diária em PDF.

Pessoa continua sem controller próprio. Conta a receber e recebimento possuem endpoints específicos para a matrícula do aluno. Chamados de manutenção possuem abertura/consulta por aluno e motorista e atendimento administrativo.

---

## 3. Arquitetura

A aplicação segue uma arquitetura em camadas:

```text
Requisição HTTP
      |
      v
Controller  ->  Service  ->  Repository  ->  PostgreSQL
      |             |
      |             +-- regras de negócio e conversões
      +-- entidades ou DTOs em JSON
```

Estrutura principal:

```text
src/main/java/com/example/integrador/
|-- Config/       CORS e relógio do dashboard
|-- Controller/   endpoints REST
|-- DTO/          objetos de entrada e resposta
|-- Enum/         valores enumerados persistidos como texto
|-- Model/        entidades JPA
|-- Repository/   Spring Data JPA e consultas agregadas do dashboard
|-- Security/     configuração do Spring Security
|-- Service/      CRUD, conversões e regras de negócio
|-- Whatsapp/     assinatura, PDFs, cliente Meta, fila, rotinas e endpoints da integração
`-- IntegradorApplication.java
```

`IntegradorApplication` habilita o agendamento com `@EnableScheduling`.

---

## 4. Tecnologias e configuração

| Item | Estado atual |
|---|---|
| Java | 21, definido em `pom.xml` |
| Spring Boot | 4.0.3 |
| Persistência | Spring Data JPA e Hibernate |
| API web | Spring Web MVC |
| Segurança | Spring Security 7, JWT HS256 stateless, BCrypt e RBAC |
| Migrações | Flyway V1–V6, incluindo JWT, administração segura, matrícula financeira, manutenção e WhatsApp |
| PDF | Apache PDFBox 3.0.8, fonte Bitstream Vera embarcada com licença |
| Validação | Jakarta Bean Validation nos contratos de autenticação, usuários, matrícula e manutenção |
| Banco | PostgreSQL |
| Redução de boilerplate | Lombok |
| Build | Maven Wrapper 3.3.4, Maven 3.9.12 configurado |
| Empacotamento | JAR, versão `0.0.1-SNAPSHOT` |

Configuração ativa de `application.properties`:

| Propriedade | Valor atual |
|---|---|
| Nome da aplicação | `integrador` |
| URL JDBC | `jdbc:postgresql://localhost:5432/integrador` |
| Usuário do banco | `postgres` |
| Senha do banco | `postgres` |
| Estratégia do schema | `spring.jpa.hibernate.ddl-auto=validate` |
| Flyway | habilitado, com baseline automático na versão 1 |
| Emissor JWT | `integrador-api` |
| Validade JWT | 2 horas (`PT2H`) |
| Origem CORS | `http://localhost:4200` |
| Exibição de SQL | habilitada |
| Limite de formulário do Tomcat | `10MB` |
| Porta HTTP | padrão do Spring Boot, normalmente `8080` |

> A senha do banco ainda está versionada em texto simples. Para outros ambientes, substitua-a por configuração externa ou variável de ambiente. Já o segredo JWT e a senha inicial do administrador são obrigatoriamente externos e não possuem valor padrão utilizável.

---

## 5. Modelo de domínio atual

Todos os identificadores são `Long` com geração `IDENTITY`, salvo indicação em contrário. Os enums são persistidos com `EnumType.STRING`.

### 5.1 Pessoa

`Pessoa` é uma entidade concreta e também a superclasse Java de `Aluno` e `Motorista`. Como a hierarquia não declara outra estratégia, o JPA usa `SINGLE_TABLE`: existe somente a tabela `pessoa`, e a coluna discriminadora `dtype` identifica `Pessoa`, `Aluno` ou `Motorista`. Não existem tabelas `aluno` e `motorista`.

- `id`: `Long`;
- `nome`: `String`;
- `cpfCnpj`: `String`;
- `telefone`: `String`;
- `tipoPessoa`: `TipoPessoa`.

### 5.2 Aluno

Estende `Pessoa` e acrescenta:

- `matricula`: `String`;
- `statusMatricula`: `StatusMatricula`;
- `dataCadastro`: `LocalDate`;
- `mensalidade`: `BigDecimal`;
- `faculdade`: relação N:1 com `Faculdade`.

No código atual, `Aluno` **não possui relação com `Rota` nem com `Viagem`**.

### 5.3 Motorista

Estende `Pessoa` e acrescenta `cnh` (`String`) e `salario` (`BigDecimal`). No modelo atual, `Motorista` **não possui associação direta com `Onibus`**. Uma viagem associa separadamente um motorista e um ônibus.

### 5.4 Usuario

Entidade separada que implementa `UserDetails` e representa exclusivamente as credenciais de uma pessoa:

- `id`: `Long`;
- `email`: `String`, usado como username;
- `senha`: hash BCrypt, retornado internamente por `getPassword()` e ignorado na serialização JSON;
- `tipoPessoa`: `TipoPessoa`, obrigatório;
- `ativo`: `boolean`, controla se a conta pode autenticar, iniciado como `true`;
- `tokenVersion`: `long`, versão interna usada para revogar JWTs, iniciada em zero;
- `pessoa`: relação 1:1 obrigatória e única com `Pessoa`, pela FK `usuario.pessoa_id`.

`getAuthorities()` produz uma autoridade no formato `ROLE_<TIPO_PESSOA>`. Expiração, bloqueio e credencial expirada retornam `true`; `isEnabled()` reflete `ativo`.

O e-mail é normalizado para minúsculas e possui unicidade case-insensitive no banco. Uma pessoa só pode estar vinculada a uma conta. Para aluno e motorista, o papel é derivado da subclasse real da pessoa e não é aceito da requisição. O primeiro administrador é criado por configuração externa; administradores autenticados podem criar outros administradores. `senha` e `tokenVersion` nunca fazem parte de `UsuarioResponse`.

### 5.5 Faculdade

- `id`: `Long`;
- `nome`: `String`;
- `endereco`: `String`;
- `telefone`: `String`.

### 5.6 Onibus

- `id`: `Long`;
- `numeroIdentificacao`: `String`;
- `placa`: `String`;
- `modelo`: `String`;
- `statusOnibus`: `StatusOnibus`;
- `fotoUrl`: `String`, persistida como `TEXT` com `@Lob`;
- `capacidade`: `int`.

O código não define restrição `unique` para `numeroIdentificacao` ou `placa`.

### 5.7 Rota

- `id`: `Long`;
- `nome`: `String`;
- `descricao`: `String`;
- `faculdade`: relação N:1 com `Faculdade`;
- `pontosEmbarque`: relação N:M com `PontoEmbarque`, com cascade `MERGE`.

### 5.8 PontoEmbarque

- `id`: `Long`;
- `ordemParada`: `Integer`;
- `descricao`: `String`;
- `rota`: relação N:1 com `Rota`, omitida do JSON por `@JsonIgnore`.

Há uma inconsistência de mapeamento: `Rota.pontosEmbarque` declara N:M, enquanto `PontoEmbarque.rota` declara N:1. Esses campos representam associações JPA separadas, pois não usam `mappedBy` entre si.

### 5.9 Viagem

- `id`: `Long`;
- `data`: `LocalDate`;
- `rota`: relação N:1 com `Rota`;
- `onibus`: relação N:1 com `Onibus`;
- `motorista`: relação N:1 com `Motorista`;
- `gradeDiaria`: relação N:1 com `GradeDiaria`;
- `faculdades`: relação N:M com `Faculdade`, usando a tabela `viagem_faculdade`.

Não existe coleção de alunos em `Viagem`.

### 5.10 GradeDiaria

- `id`: `Long`;
- `diaSemana`: `DiaSemana`;
- `descricao`: `String`;
- `viagens`: relação 1:N com `Viagem`, `mappedBy="gradeDiaria"`, cascade `ALL` e remoção de órfãos.

A entidade não possui campo `data`. O campo `data` existe apenas em `GradeDiariaDTO` e, portanto, não é persistido.

### 5.11 ContaPagar

- `id`: `Long`;
- `descricao`: `String`;
- `valor`: `BigDecimal`;
- `dataVencimento`: `LocalDate`;
- `status`: `StatusFinanceiro`;
- `motorista`: relação N:1 com `Pessoa`, armazenada pela coluna `pessoa_id`.

Embora o atributo se chame `motorista`, seu tipo permite qualquer registro de `Pessoa`.

### 5.12 Pagamento

- `id`: `Long`;
- `valor`: `BigDecimal`;
- `dataPagamento`: `LocalDate`;
- `formaPagamento`: `FormaPagamento`;
- `contaPagar`: relação N:1 com `ContaPagar`, pela coluna `conta_pagar_id`.

### 5.13 ContaReceber

- `id`: `Long`;
- `valor`: `BigDecimal`;
- `descricao`: `String`;
- `dataVencimento`: `LocalDate`;
- `status`: `StatusFinanceiro`;
- `aluno`: relação N:1 obrigatória com `Aluno`.

Cada aluno pode possuir várias cobranças, mas existe no máximo uma por aluno e data de vencimento.

### 5.14 Recebimento

- `id`: `Long`;
- `valor`: `BigDecimal`;
- `dataRecebimento`: `LocalDate`;
- `formaPagamento`: `FormaPagamento`;
- `contaReceber`: relação 1:1 obrigatória com `ContaReceber`.

Uma conta pode possuir no máximo um recebimento, impedindo pagamento duplicado.

### 5.15 ChamadoManutencao

- `id`: `Long`;
- `descricao`: `String`, obrigatória, até 2.000 caracteres;
- `dataAbertura`: `LocalDate`, obrigatória e definida pelo servidor;
- `urgencia`: `NivelUrgencia`, obrigatória, inicialmente `MEDIA`;
- `status`: `StatusChamado`, obrigatório, inicialmente `ABERTO`;
- `onibus`: relação N:1 obrigatória com `Onibus`;
- `solicitante`: relação N:1 obrigatória com `Pessoa`, mapeada na coluna legada `responsavel_id`;
- `observacaoAdmin`: `String` opcional, até 2.000 caracteres, obrigatória ao resolver.

Possui controller, DTOs, repository e serviço com autorização. Não expõe CRUD genérico nem exclusão. A autoria vem do usuário autenticado pelo JWT e não é aceita do cliente. Excluir somente o acesso `Usuario` preserva a pessoa e seus chamados. Pessoa/ônibus com chamados não pode ser excluído fisicamente.

---

## 6. DTOs e contratos específicos

### AlunoDTO

Usado em `POST` e `PUT` de alunos: `id`, `nome`, `cpfCnpj`, `telefone`, `matricula`, `mensalidade` e `faculdadeId`. `mensalidade` é obrigatória e deve ser maior ou igual a `0.01`.

Não aceita `statusMatricula`, `dataCadastro` ou `tipoPessoa`: esses campos são controlados pelo backend.

### MotoristaDTO

Usado nas entradas e respostas de motoristas: `id`, `nome`, `cpfCnpj`, `cnh`, `salario` e `telefone`. Não contém ônibus nem `tipoPessoa`.

### ViagemDTO

- `id`, `data`;
- `rotaId`, `rotaNome`;
- `onibusId`, `onibusPlaca`, `numeroIdentificacao`;
- `motoristaId`, `motoristaNome`;
- `faculdades`: lista de objetos `Faculdade`.

Não contém `gradeDiariaId`. Na conversão de entidade para DTO, a lista de faculdades só é copiada dentro da condição que verifica se há motorista; uma viagem sem motorista pode responder com `faculdades` vazia mesmo que a relação esteja preenchida.

### GradeDiariaDTO

Contém `id`, `data`, `diaSemana`, `descricao` e `viagens` (`List<ViagemDTO>`). O campo `data` não corresponde a nenhum campo da entidade e não é preenchido pelo construtor de resposta.

### ReciboDTO

Contém `id`, `valor`, `dataPagamento`, `formaPagamento`, `nomeMotorista` e `descricaoConta`. O nome vem de `pagamento.contaPagar.motorista.nome`. Não há data de vencimento no DTO atual.

---

## 7. API REST disponível

URL base local esperada: `http://localhost:8080`.

Salvo onde indicado, os controllers não definem códigos HTTP específicos: criações e atualizações normalmente respondem `200 OK`, e exclusões respondem `200 OK` sem corpo.

### 7.1 Alunos

| Método | Caminho | Entrada | Saída |
|---|---|---|---|
| GET | `/aluno/listar` | — | lista de `Aluno` |
| GET | `/aluno/buscar-aluno/{id}` | id | `Aluno` |
| POST | `/aluno/salvar-aluno` | `AlunoDTO` | `Aluno` |
| PUT | `/aluno/atualizar-aluno/{id}` | `AlunoDTO` | `Aluno` |
| DELETE | `/aluno/deletar-aluno/{id}` | id | sem corpo |

### 7.2 Motoristas

| Método | Caminho | Entrada | Saída |
|---|---|---|---|
| GET | `/motorista/listar` | — | lista de `MotoristaDTO` |
| GET | `/motorista/buscar-motorista/{id}` | id | `MotoristaDTO` |
| POST | `/motorista/salvar-motorista` | `MotoristaDTO` | `MotoristaDTO` |
| PUT | `/motorista/atualizar-motorista/{id}` | `MotoristaDTO` | `MotoristaDTO` |
| DELETE | `/motorista/deletar-motorista/{id}` | id | sem corpo |

### 7.3 Ônibus

| Método | Caminho |
|---|---|
| GET | `/onibus/listar` |
| GET | `/onibus/buscar-onibus/{id}` |
| POST | `/onibus/salvar-onibus` |
| PUT | `/onibus/atualizar-onibus/{id}` |
| DELETE | `/onibus/deletar-onibus/{id}` |

POST e PUT recebem a entidade `Onibus` em JSON.

### 7.4 Faculdades

| Método | Caminho |
|---|---|
| GET | `/faculdade/listar` |
| GET | `/faculdade/buscar-faculdade/{id}` |
| POST | `/faculdade/salvar-faculdade` |
| PUT | `/faculdade/atualizar-faculdade/{id}` |
| DELETE | `/faculdade/deletar-faculdade/{id}` |

POST e PUT recebem a entidade `Faculdade` em JSON.

### 7.5 Rotas

| Método | Caminho |
|---|---|
| GET | `/rota/listar` |
| GET | `/rota/buscar-rota/{id}` |
| POST | `/rota/salvar-rota` |
| PUT | `/rota/{id}` |
| DELETE | `/rota/deletar-rota/{id}` |

POST e PUT recebem `Rota`, incluindo faculdade e pontos quando aplicável. O caminho de atualização é `/rota/{id}`, não `/rota/atualizar-rota/{id}`.

### 7.6 Pontos de embarque

Os segmentos internos usam `pontoEmbarque` com E maiúsculo; os caminhos são sensíveis à grafia.

| Método | Caminho |
|---|---|
| GET | `/ponto-embarque/listar` |
| GET | `/ponto-embarque/buscar-pontoEmbarque/{id}` |
| POST | `/ponto-embarque/salvar-pontoEmbarque` |
| PUT | `/ponto-embarque/atualizar-pontoEmbarque/{id}` |
| DELETE | `/ponto-embarque/deletar-pontoEmbarque/{id}` |

POST e PUT recebem `PontoEmbarque`.

### 7.7 Viagens

| Método | Caminho | Entrada/saída |
|---|---|---|
| GET | `/viagem/listar` | lista de `ViagemDTO` |
| GET | `/viagem/buscar-viagem/{id}` | `ViagemDTO` |
| POST | `/viagem/salvar-viagem` | recebe e devolve `ViagemDTO` |
| PUT | `/viagem/atualizar-viagem/{id}` | recebe e devolve `ViagemDTO` |
| DELETE | `/viagem/deletar-viagem/{id}` | sem corpo |

Exceções legadas `RuntimeException` nesse controller são convertidas em `400 Bad Request` com a mensagem no corpo. Conflitos de atribuição de ônibus retornam `409` e ônibus inexistente retorna `404`, ambos com `ProblemDetail`.

### 7.8 Grades diárias

| Método | Caminho | Entrada/saída |
|---|---|---|
| GET | `/grade-diaria/listar` | lista de `GradeDiariaDTO` |
| GET | `/grade-diaria/buscar-grade/{id}` | `GradeDiariaDTO` |
| POST | `/grade-diaria/salvar-grade` | recebe e devolve `GradeDiariaDTO` |
| PUT | `/grade-diaria/atualizar-grade/{id}` | recebe e devolve `GradeDiariaDTO` |
| DELETE | `/grade-diaria/deletar-grade/{id}` | sem corpo |

Exceções legadas `RuntimeException` nesse controller são convertidas em `400 Bad Request`. Conflitos de atribuição de ônibus retornam `409` e ônibus inexistente retorna `404`, ambos com `ProblemDetail`.

### 7.9 Contas a pagar

| Método | Caminho |
|---|---|
| GET | `/contapagar/listar` |
| GET | `/contapagar/buscar-contapagar/{id}` |
| POST | `/contapagar/salvar-contapagar` |
| PUT | `/contapagar/atualizar-contapagar/{id}` |
| DELETE | `/contapagar/deletar-contapagar/{id}` |

POST e PUT recebem `ContaPagar`.

### 7.10 Pagamentos e recibos

| Método | Caminho | Parâmetros | Resultado |
|---|---|---|---|
| POST | `/pagamentos/realizar/{contaId}` | query obrigatória `formaPagamento`; query opcional `valorPago` | `Pagamento` |
| GET | `/pagamentos/recibo/conta/{contaId}` | id da conta | `ReciboDTO` ou `404 Not Found` |

Exemplo:

```http
POST /pagamentos/realizar/10?formaPagamento=PIX&valorPago=1500.00
```

Não existem endpoints genéricos de listar, buscar por id, atualizar ou excluir pagamentos.

### 7.11 Autenticação

| Método | Caminho | Acesso | Resultado |
|---|---|---|---|
| POST | `/auth/login` | público | JWT e resumo seguro do usuário |
| GET | `/auth/me` | autenticado | usuário, pessoa e perfil específico do papel |

O login recebe `email` e `senha`. A resposta contém `accessToken`, `tokenType="Bearer"`, `expiresIn=7200` e `usuario`. O resumo do usuário inclui `ativo`, mas nunca senha ou versão interna. O JWT possui somente `sub`, `email`, `pessoaId`, `roles`, `ver`, `iat`, `exp` e `iss`. `ver` corresponde à versão de segurança atual da conta.

### 7.12 Administração de usuários

Todos estes endpoints exigem `ROLE_ADMIN`:

| Método | Caminho | Função |
|---|---|---|
| GET | `/usuarios` | lista contas sem o hash da senha |
| GET | `/usuarios/pessoas-sem-usuario` | lista alunos e motoristas ainda sem conta |
| POST | `/usuarios` | cria conta para uma pessoa existente |
| POST | `/usuarios/admin` | cria uma pessoa-base e sua conta administrativa |
| PATCH | `/usuarios/{id}` | altera somente o e-mail e retorna a conta segura |
| PUT | `/usuarios/{id}/senha` | redefine a senha e retorna `204 No Content` |
| PATCH | `/usuarios/{id}/status` | desativa ou reativa a conta e retorna a conta segura |
| DELETE | `/usuarios/{id}` | exclui somente o acesso e retorna `204 No Content` |

`POST /usuarios` recebe `pessoaId`, `email` e `senha`. O papel é derivado pelo backend. E-mail repetido ou pessoa já vinculada retorna `409 Conflict`.

Os novos corpos são, respectivamente, `{"email":"novo@email.com"}`, `{"senha":"NovaSenha123"}` e `{"ativo":false}`. E-mail e status iguais ao valor atual são idempotentes. Alterar e-mail, redefinir senha, desativar ou reativar incrementa `tokenVersion` e invalida imediatamente os tokens anteriores.

Não é permitido desativar ou excluir a própria conta, nem remover o último ADMIN ativo. Essas violações e e-mail duplicado retornam `409 Conflict`; id inexistente retorna `404` e validação inválida retorna `400`. A exclusão apaga apenas `Usuario`: a `Pessoa` permanece e, no caso de ALUNO ou MOTORISTA, volta a aparecer em `/usuarios/pessoas-sem-usuario`.

### 7.13 Viagens do motorista autenticado

`GET /viagem/minhas` exige `ROLE_MOTORISTA`. O serviço usa o claim `pessoaId` do JWT e ignora qualquer tentativa de o cliente escolher outro motorista, retornando apenas as viagens vinculadas à pessoa autenticada.

### 7.14 Matrícula e contas a receber

| Método | Caminho | Acesso | Resultado |
|---|---|---|---|
| GET | `/contareceber/listar` | ADMIN | todas as contas a receber |
| GET | `/contareceber/minhas` | ALUNO | somente as cobranças do aluno autenticado |
| POST | `/recebimentos/realizar/{contaId}?formaPagamento=PIX` | ALUNO | registra o valor integral e retorna o recibo |
| GET | `/recebimentos/recibo/conta/{contaId}` | ALUNO | recibo de uma cobrança própria paga |

O aluno não envia `alunoId`, `pessoaId` nem valor. O backend usa `pessoaId` do JWT e o valor persistido em `conta_receber`. Tentativas de acessar cobrança ou recibo de outro aluno retornam `404`; cobrança já paga retorna `409`.

### 7.15 Chamados de manutenção

| Método | Caminho | Acesso | Resultado |
|---|---|---|---|
| POST | `/chamados-manutencao` | ALUNO/MOTORISTA | abre chamado, `201`, corpo e `Location` |
| GET | `/chamados-manutencao/minhas` | ALUNO/MOTORISTA | próprios chamados, mais recentes primeiro |
| GET | `/chamados-manutencao/onibus` | ADMIN/ALUNO/MOTORISTA | todos os ônibus em resumo seguro para seleção |
| GET | `/chamados-manutencao` | ADMIN | todos; filtros opcionais `status`, `urgencia`, `onibusId` |
| GET | `/chamados-manutencao/{id}` | ADMIN ou autor | detalhes; chamado alheio retorna `404` |
| PUT | `/chamados-manutencao/{id}/atendimento` | ADMIN | atendimento atualizado, `200` |

POST recebe somente `{"onibusId":1,"descricao":"Ar-condicionado quebrado"}`. O servidor localiza `Usuario` pelo `sub` do JWT validado e obtém sua `Pessoa`; define autor, data, `ABERTO` e `MEDIA`. Campos extras não permitem alterar esses valores. Ônibus inexistente retorna `404` e descrição vazia/maior que 2.000 retorna `400`.

PUT exige `urgencia` e `status`. Recebe `observacaoAdmin` (resposta atual; omitida/nula é apagada) e opcionalmente `statusOnibus`. Exemplo: `{"urgencia":"ALTA","status":"EM_ANDAMENTO","observacaoAdmin":"Encaminhado à oficina","statusOnibus":"MANUTENCAO"}`. Observação não vazia é obrigatória ao resolver; reabrir é permitido. Sem `statusOnibus`, o estado do ônibus permanece inalterado. A atualização de chamado e ônibus ocorre na mesma transação, sem sobrescrever os demais campos do veículo.

As respostas contêm `id`, `descricao`, `dataAbertura`, `urgencia`, `status`, `observacaoAdmin`, `onibus` (`id`, `numeroIdentificacao`, `placa`, `modelo`, `statusOnibus`) e `solicitante` (`pessoaId`, `nome`, `tipoPessoa`). Não expõem CPF, telefone, foto, senha ou entidade de usuário. A autoria referencia a pessoa e não congela o nome em uma cópia histórica. Não há paginação de servidor; as listas são arrays.

Não há edição do relato/ônibus/autor, cancelamento, exclusão, anexos, chat ou notificações. A consulta e o atendimento não alteram automaticamente o estado do veículo.

Guia específico: `GUIA_MANUTENCAO_ONIBUS_FRONTEND.md`, com exemplos Angular standalone/PrimeNG; o código do frontend não foi modificado nesta entrega.

### 7.16 Dashboards por nível de acesso

| Método | Caminho | Acesso exclusivo | Conteúdo |
|---|---|---|---|
| GET | `/dashboard/aluno` | ALUNO | perfil resumido, faculdade, transporte e cobranças próprias |
| GET | `/dashboard/motorista` | MOTORISTA | viagens atribuídas à pessoa autenticada e itinerários |
| GET | `/dashboard/admin?mes=2026-10` | ADMIN | movimentações mensais, contas abertas e indicadores operacionais |

São consultas com DTOs próprios, `@PreAuthorize` nos serviços e transação somente de leitura. A identidade de aluno/motorista é obtida por `Usuario` identificado pelo `sub` do JWT validado. Não há parâmetro de pessoa, faculdade ou motorista para escolher outro perfil. ADMIN não acessa os endpoints exclusivos dos demais papéis.

Todas as respostas incluem `dataReferencia` e `geradoEm`, derivados de um `Clock` injetável em `America/Sao_Paulo`. `mes` omitido significa o mês atual; deve ser `yyyy-MM`, com ano entre 0001 e 9999. Mês inválido retorna `400 ProblemDetail`; ausência/token inválido retorna `401`, papel incorreto `403`.

**Transporte:** `viagensHoje`, `agendaSemanal`, `programacaoSemGrade` e `rotas`. A agenda cobre hoje e os seis dias seguintes e calcula cada ocorrência somente pelo dia da grade, ordenando por ocorrência e ID. Todas as viagens de hoje são destacadas. `Viagem.data` não é consultada para o dashboard, não é alterada e continua nos contratos antigos. Grade ausente ou sem dia deixa a ocorrência nula, com aviso “Programação não definida”. Não há horários ou escolha de “próxima saída”.

Os resumos incluem grade, rota/descrição/pontos, ônibus (ID, número, placa, modelo, capacidade, estado), motorista (ID da pessoa/nome), destinos explícitos e avisos. Não incluem CPF, CNH, salário, outros alunos ou foto do ônibus. Ônibus não OPERANDO ou sem estado permanece visível com alerta e `onibusDisponivel=false`, sem cancelamento automático. Pontos reúnem os dois vínculos do modelo, deduplicam por ID e ordenam por ordemParada, com ordem ausente ao final.

**Aluno:** seleciona viagens cuja coleção `Viagem.faculdades` contém a faculdade do cadastro, não apenas a faculdade principal da rota. Mostra rotas cadastradas para a faculdade e rotas usadas por essas viagens, inclusive rotas sem viagem. Não representa vínculo individual aluno–ônibus/ponto. Faculdade ou transporte ausente retorna `200`, arrays vazios e avisos.

`financeiro` separa `mensalidadeCadastro` do valor de `cobrancaPrincipal`. A principal é a aberta vencida mais antiga; na ausência, a aberta com vencimento mais próximo; se todas pagas, a paga de vencimento mais recente (ID desempata). Retorna valor, vencimento, status persistido, situação calculada e dias para vencer, além de quantidade/valor em aberto e vencido. Situações: `PAGO`, `VENCIDA`, `VENCE_HOJE`, `A_VENCER`, `SEM_VENCIMENTO`. O contador é negativo para vencidas, zero hoje e nulo para pagas/sem data. Pagas nunca são vencidas. Sem conta, a principal é nula: nenhum prazo é inventado.

**Motorista:** somente viagens cujo motorista seja a pessoa autenticada. Usa os mesmos resumos e agenda, sem ampliar acesso a cadastros ou financeiro.

**ADMIN:** `financeiro` contém recebido/pago no mês pelas datas e valores efetivos de `Recebimento`/`Pagamento`, saldo dessas movimentações e série de seis meses encerrando no selecionado, incluindo meses zerados. Saldo pode ser negativo e não representa lucro ou saldo bancário. As agregações financeiras não juntam relacionamentos que multiplicariam valores; usam `BigDecimal`.

Contas abertas a receber/pagar, vencidas e vencendo em sete dias são posição atual de **todos os períodos**, não filtrada pelo mês selecionado. A janela de vencimentos inclui hoje até hoje+6. Vencido significa não PAGO e vencimento anterior à referência; status legado nulo é tratado como aberto, sem vencimento não é contado como vencido. Cinco contas abertas mais antigas de cada tipo são retornadas, ordenadas por vencimento e ID; sem vencimento ficam ao final.

`operacao` traz alunos por matrícula, ônibus por estado (incluindo não informado), viagens de hoje, viagens sem programação, chamados abertos/em andamento e prioridade ALTA pendente, além dos cinco chamados pendentes ordenados por urgência e antiguidade. Ausência de dados retorna totais zero e listas vazias.

Não são necessárias novas tabelas/migrations. Guia específico: `GUIA_DASHBOARD_FRONTEND.md`, adaptado ao Angular standalone/PrimeNG atual, com contratos, navegação, layouts, pagamento compartilhado e testes sugeridos. O frontend não foi modificado nesta entrega.

### 7.17 Programação individual por WhatsApp

| Método | Caminho | Acesso | Resultado |
|---|---|---|---|
| GET | `/whatsapp/minha-assinatura` | ALUNO/MOTORISTA | situação própria, sem hash/código |
| POST | `/whatsapp/minha-assinatura/ativacao` | ALUNO/MOTORISTA | assinatura pendente e link de confirmação |
| DELETE | `/whatsapp/minha-assinatura` | ALUNO/MOTORISTA | cancela recebimento, 204 |
| GET | `/whatsapp/minha-programacao/pdf` | ALUNO/MOTORISTA | PDF próprio do dia, sem cache; 404 sem viagens |
| GET/PUT | `/integracoes/whatsapp/configuracao` | ADMIN | consulta segura / ativa ou desativa automático |
| GET | `/integracoes/whatsapp/previa?usuarioId=...` | ADMIN | programação, elegibilidade e impedimentos |
| GET | `/integracoes/whatsapp/previa/pdf?usuarioId=...` | ADMIN | PDF da prévia individual, sem envio |
| POST | `/integracoes/whatsapp/envios` | ADMIN | lote assíncrono, 202 |
| GET | `/integracoes/whatsapp/envios?pagina=0&tamanho=20` | ADMIN | histórico paginado; tamanho máximo 100 |
| GET | `/integracoes/whatsapp/envios/{id}` | ADMIN | contadores e entregas com números mascarados |
| GET/POST | `/webhooks/whatsapp` | verificação Meta | desafio / eventos com assinatura HMAC |

A assinatura pertence à conta `Usuario`, não altera `Pessoa.telefone` e exige conta ativa. Os endpoints pessoais obtêm a identidade pelo `sub` do JWT. POST de ativação recebe `numero` internacional e `aceite=true`. Cria código aleatório de uso único, válido por 30 minutos, armazenado só como SHA-256, e permite cinco pedidos por conta/hora. Somente uma mensagem `ATIVAR <codigo>` do próprio número, validada pelo webhook, muda a assinatura para ATIVA. Trocar o número suspende o anterior e invalida o código anterior. Um número não pode ter duas assinaturas ativas. Cancelamento pelo perfil ou por SAIR/PARAR/CANCELAR impede novos envios.

PUT configuração recebe `{"automatico":true}` ou false. Antes de habilitar, verifica credenciais, webhook e aprovação/formato do template na Meta. Desabilitar não depende da disponibilidade externa. Credenciais nunca são devolvidas ao Angular.

POST de envio recebe `requestId` UUID, `publico` (TODOS/ALUNOS/MOTORISTAS), `reenviar` e `motivo`. Mesmo UUID com os mesmos parâmetros retorna o lote existente; parâmetros diferentes retornam 409. Correção usa novo UUID, reenviar=true e motivo obrigatório de até 500 caracteres. A data é sempre o dia atual em São Paulo, sem envio retroativo.

A consulta da agenda foi extraída para um serviço interno compartilhado com o dashboard. Aluno recebe destinos explícitos da faculdade; motorista recebe atribuições próprias. A matrícula não filtra o recebimento: conta ativa, assinatura confirmada e programação são os critérios. Sem faculdade/programação ou número fora da lista de teste, a entrega é suprimida. Datas legadas permanecem intactas e são ignoradas. Pontos são deduplicados/ordenados; veículos indisponíveis aparecem com aviso.

PDFs são gerados em memória com fonte livremente redistribuível, quebra de texto, paginação e rodapé. Contêm programação, ônibus, motorista, rotas, pontos, destinos e avisos; não contêm CPF, CNH, salários, cobranças, outros alunos, fotos grandes ou horários inventados. São enviados por upload de mídia e template com cabeçalho DOCUMENT, idioma pt_BR e três parâmetros (nome, data, referência), sem URLs públicas.

O módulo possui configuração global, assinatura, lote e entrega. Programação JSON/PDF é congelada na preparação da entrega. Assinatura é removida ao excluir a conta; solicitante/destinatário nas relações do histórico ficam nulos, preservando ID original, documento e resultado sem bloquear exclusões já permitidas.

O webhook GET valida Verify Token e retorna hub.challenge. POST valida `X-Hub-Signature-256` sobre os bytes originais com App Secret, WABA ID e Phone Number ID. Aceita eventos somente da conta/número configurados. Eventos de entrega não regridem estados confirmados; correlação opaca permite reconhecer webhook que chegue antes da resposta HTTP.

Erros seguem ProblemDetail: 400 validação, 401/403 autenticação/permissão ou assinatura inválida, 404 inexistência/PDF sem programação, 409 conflito/limite/requestId e 503 integração indisponível. HTTP 202 significa fila registrada, não entrega.

Guia completo: `GUIA_WHATSAPP_FRONTEND.md`, incluindo conta Meta, template, webhook, variáveis, perfil, tela ADMIN, interfaces, serviços, PDFs autenticados e testes. Angular não foi alterado nesta entrega. A conta Meta e os envios reais precisam ser configurados/validados separadamente.

### 7.18 Recursos sem endpoint

Não há controller para `Pessoa`. Conta a receber, recebimento e chamado de manutenção expõem somente os contratos específicos descritos acima, sem CRUD genérico completo.

---

## 8. Regras de negócio implementadas

### 8.1 Primeira conta de salário

Ao cadastrar um motorista pela API, `MotoristaService` salva o motorista e cria uma `ContaPagar` com descrição `Salário - <nome>`, o salário como valor, vencimento em 30 dias e status `PENDENTE`.

Não há transação envolvendo explicitamente as duas gravações. Uma falha ao criar a conta pode ocorrer depois de o motorista ter sido salvo.

### 8.2 Geração recorrente de contas

`RotinaFinanceiraService` executa diariamente às **02:00**, pelo cron `0 0 2 * * *`.

Para cada motorista, procura a conta com vencimento mais recente. Se ela vencer hoje ou já estiver vencida, cria outra com vencimento 30 dias depois da última. Se o motorista não tiver conta anterior, a rotina não cria a primeira conta.

Para cada aluno com mensalidade maior que zero, aplica a mesma lógica em `conta_receber`. Se ainda não existir cobrança, cria a primeira com vencimento em 30 dias; quando a última vence, cria somente uma nova com vencimento 30 dias depois. A restrição por aluno/data evita duplicidade.

### 8.3 Pagamento de conta

- a conta precisa existir;
- contas com status `PAGO` não podem ser pagas novamente;
- a data é definida como a data atual;
- sem `valorPago`, usa-se o valor integral da conta;
- após gravar o pagamento, a conta é marcada como `PAGO`.

O código também marca a conta como paga quando `valorPago` é inferior ao valor da conta; não existe controle de saldo ou pagamento parcial.

### 8.4 Pagamento da matrícula

Ao cadastrar um aluno, `AlunoService` define `dataCadastro` com a data atual, `statusMatricula=PENDENTE` e cria a primeira `ContaReceber` na mesma transação. A conta usa a mensalidade informada, descrição `Matrícula - <nome>`, vencimento em 30 dias e status `PENDENTE`. Ao editar um aluno antigo que ainda não possui cobrança, a primeira conta é criada automaticamente.

O pagamento sempre usa o valor integral persistido na conta. Dentro de uma transação, o backend bloqueia a conta, valida que ela pertence ao `pessoaId` do JWT, impede pagamento duplicado, cria `Recebimento`, marca a conta como `PAGO` e define `statusMatricula=ATIV0`. A operação representa um registro financeiro interno; não existe integração com banco, PIX ou operadora de cartão.

### 8.5 Validação de capacidade da viagem

Ao criar ou atualizar uma viagem com ônibus e faculdades, o serviço soma todos os alunos de cada faculdade cujo status seja `ATIV0` e rejeita a operação quando o total excede `Onibus.capacidade`.

A validação considera todos os alunos ativos das faculdades, não alunos individualmente vinculados à viagem.

### 8.6 Grade diária e viagens

Ao salvar uma grade, as viagens do DTO são convertidas e associadas a ela. Ao atualizar com uma lista não nula, viagens cujos IDs pertencem à grade são atualizadas preservando sua identidade; sem ID são consideradas novas. IDs de outra grade ou repetidos são rejeitados. Como há cascade `ALL` e `orphanRemoval=true`, viagens omitidas da lista podem ser excluídas do banco. Enviar lista nula mantém a coleção.

### 8.7 Disponibilidade do ônibus e manutenção

Somente um ônibus `OPERANDO` pode ser atribuído a uma nova viagem ou substituir o ônibus de uma viagem existente. A regra vale tanto na API de viagens quanto nas viagens das grades e retorna `409` com mensagem explicativa. Estado nulo também impede nova atribuição. É permitido manter o mesmo ônibus numa viagem existente e editar outros dados, mesmo após ele ficar indisponível.

Abrir, resolver ou reabrir um chamado nunca altera automaticamente o ônibus ou cancela viagens. ADMIN escolhe manualmente o estado do ônibus durante o atendimento ou no cadastro. Resolver exige observação, inclusive para chamados duplicados/improcedentes. Pessoa/ônibus com qualquer chamado, inclusive resolvido, tem exclusão física bloqueada com `409`; excluir somente as credenciais continua permitido pelas regras de administração de usuários.

### 8.8 Agenda semanal e consultas sem efeitos colaterais

O dashboard ignora a data legada da viagem e calcula a próxima ocorrência do dia da grade dentro de uma janela de sete dias. Viagem de segunda aparece na segunda pertinente à janela mesmo quando sua data legada é antiga, futura ou nula. Viagem sem grade/dia nunca ganha programação pela data antiga. Os dias atualmente aceitos vão de segunda a sexta; não existem horários, rastreamento ou reservas individuais.

Consultar um dashboard não executa a rotina financeira, não cria conta, não muda matrícula e não atualiza estado financeiro por atraso. O vencimento da cobrança não expira matrícula ou acesso. Pagamento continua usando o fluxo já existente de recebimentos; o frontend deve consultar novamente o painel depois de pagar para obter a nova cobrança principal, totais e perfil.

### 8.9 Agendamento e confiabilidade do WhatsApp

Integração e automático começam desativados. A rotina verifica a criação às 15h e a recuperação a cada minuto entre 15h e antes das 18h, sempre em America/Sao_Paulo. Um lote automático é único por dia. Se o manual já preparou a programação para o destinatário, o automático não repete. Supressão sem PDF não representa mensagem enviada e permite cadastro corrigido; correção de uma programação já preparada exige reenvio explícito.

Fila persistida com reserva atômica, bloqueio curto da configuração global e ritmo de reserva de no máximo uma entrega por segundo entre workers. PDF e comunicação externa ficam fora dos bloqueios de banco. Conta, assinatura, revisão e número são conferidos na reserva e novamente depois do upload, antes da mensagem. Não é possível recolher uma mensagem já aceita pela Meta.

Estados: PENDENTE, PROCESSANDO, ACEITA, ENVIADA, ENTREGUE, LIDA, FALHA, INCERTA, SUPRIMIDA e EXPIRADA. Aceite HTTP não comprova entrega. Webhook confirma envio/entrega/leitura. Falhas comprovadas anteriores ao aceite têm até três novas tentativas após 1, 5 e 15 minutos. Timeout/5xx de mensagem ou resposta ambígua fica INCERTA, sem repetição automática; falha de upload não significa mensagem enviada. Reserva PROCESSANDO abandonada há mais de dez minutos fica INCERTA.

Pendências automáticas expiram às 18h; manuais não atravessam a meia-noite. O backend precisa permanecer ligado; frontend fechado não interrompe a rotina. Não há calendário de feriados ou envio de dias anteriores, grupos, integração por WhatsApp Web ou chatbot geral.

---

## 9. Enumerações aceitas

Os valores devem ser enviados exatamente como escritos.

| Enum | Valores atuais |
|---|---|
| `DiaSemana` | `SEGUNDA`, `TERCA`, `QUARTA`, `QUINTA`, `SEXTA` |
| `FormaPagamento` | `PIX`, `DINHEIRO`, `CARTAO`, `BOLETO`, `TRANSFERENCIA` |
| `NivelUrgencia` | `BAIXA`, `MEDIA`, `ALTA` |
| `StatusChamado` | `ABERTO`, `EM_ANDAMENTO`, `RESOLVIDO` |
| `StatusFinanceiro` | `PENDENTE`, `PAGO`, `ATRASADO` |
| `StatusMatricula` | `ATIV0`, `INATIV0`, `PENDENTE` |
| `StatusOnibus` | `OPERANDO`, `MANUTENCAO`, `QUEBRADO`, `INATIVO` |
| `TipoPessoa` | `ADMIN`, `ALUNO`, `MOTORISTA` |

> `ATIV0` e `INATIV0` terminam com o algarismo zero no código-fonte. Enviar `ATIVO` ou `INATIVO` para `StatusMatricula` causa erro de desserialização.

---

## 10. Segurança e CORS

### Segurança efetiva

A API usa sessão `STATELESS` e OAuth2 Resource Server para validar JWT próprio, sem filtro artesanal. O token é assinado com HS256 por uma chave Base64 externa, tem emissor `integrador-api` e expira em duas horas. CSRF está desabilitado porque a autenticação ocorre exclusivamente pelo cabeçalho Bearer.

As senhas são armazenadas apenas com BCrypt. O JWT é assinado, não criptografado, e por isso não contém senha, CPF/CNPJ, telefone ou outros dados desnecessários. Em cada requisição autenticada, o validador consulta `usuario` pelo `sub` e compara o claim `ver` com `token_version`. Conta inexistente, inativa ou com versão divergente recebe `401`, permitindo revogação imediata após alterações sensíveis. Tokens antigos sem `ver` também recebem `401`.

O logout comum continua local: se nenhuma alteração de segurança ocorrer, o token descartado pelo frontend permanece válido até `exp`. Editar e-mail, redefinir senha, mudar status ou excluir a conta invalida os tokens no servidor imediatamente.

Permissões efetivas:

| Recurso | Público | ADMIN | MOTORISTA | ALUNO |
|---|---:|---:|---:|---:|
| `POST /auth/login` e `OPTIONS` | sim | sim | sim | sim |
| `GET /auth/me` | não | sim | sim | sim |
| `/whatsapp/**` | não | não | sim | sim |
| `/integracoes/whatsapp/**` | não | sim | não | não |
| GET/POST `/webhooks/whatsapp` | verificação Meta | verificação Meta | verificação Meta | verificação Meta |
| `GET /dashboard/admin` | não | sim | não | não |
| `GET /dashboard/motorista` | não | não | sim | não |
| `GET /dashboard/aluno` | não | não | não | sim |
| `/usuarios/**` | não | sim | não | não |
| POST `/chamados-manutencao` e GET `/chamados-manutencao/minhas` | não | não | sim | sim |
| GET `/chamados-manutencao/onibus` | não | sim | sim | sim |
| GET `/chamados-manutencao/{id}` | não | todos | próprios | próprios |
| Listagem geral e atendimento de manutenção | não | sim | não | não |
| `GET /viagem/minhas` | não | não | sim | não |
| `GET /contareceber/minhas` | não | não | não | sim |
| POST/GET de `/recebimentos/**` | não | não | não | sim |
| GET de `/rota/**`, `/grade-diaria/**` e `/ponto-embarque/**` | não | sim | não | sim |
| Demais contas, pagamentos e financeiro | não | sim | não | não |
| Demais rotas existentes | não | sim | não | não |

Ausência, expiração, adulteração, versão divergente ou conta desabilitada/excluída retorna `401`. Papel insuficiente retorna `403`. Além das regras por URL, os serviços sensíveis usam `@PreAuthorize`.

### CORS efetivo

O CORS é centralizado em `/**`. Somente `http://localhost:4200` é aceito, para `GET`, `POST`, `PUT`, `DELETE`, `PATCH` e `OPTIONS`, com os cabeçalhos `Authorization` e `Content-Type`. Os `@CrossOrigin("*")` foram removidos dos controllers.

### Formato de erros

As falhas novas seguem `application/problem+json`/`ProblemDetail`: validação `400`, autenticação `401`, autorização `403`, recurso inexistente `404` e conflito `409`. Credenciais incorretas sempre usam a mensagem genérica “E-mail ou senha inválidos”. Alguns controllers antigos ainda possuem tratamento local de `RuntimeException` em texto e permanecem listados como dívida técnica.

---

## 11. Execução local

### Pré-requisitos

- JDK 21;
- PostgreSQL em execução;
- banco `integrador` criado;
- acesso ao Maven Central na primeira resolução de dependências.

Configuração esperada pelo arquivo atual:

```text
host: localhost
porta: 5432
banco: integrador
usuário: postgres
senha: postgres
```

Antes da primeira inicialização, configure uma chave JWT Base64 de pelo menos 32 bytes e os dados do administrador inicial. Exemplo para PowerShell:

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$env:BOOTSTRAP_ADMIN_NAME = "Administrador"
$env:BOOTSTRAP_ADMIN_EMAIL = "admin@exemplo.com"
$env:BOOTSTRAP_ADMIN_PASSWORD = "troque-por-uma-senha-forte"
```

Esses valores valem apenas para o processo/terminal atual e não devem ser gravados no Git. Se já existir um usuário `ADMIN`, o bootstrap não cria outro e as três variáveis de administrador deixam de ser necessárias. `JWT_SECRET` continua obrigatório em toda inicialização.

Com o build corrigido, os comandos previstos para Windows são:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Em Linux/macOS:

```bash
./mvnw test
./mvnw spring-boot:run
```

### Migrações do banco

O elemento Maven inválido que bloqueava a compilação foi removido. O schema agora é administrado pelo Flyway:

- `V1__baseline_schema.sql`: descreve o schema completo para bancos novos;
- `V2__jwt_usuario_pessoa.sql`: preenche `pessoa.tipo_pessoa`, corrige o check de matrícula e adiciona a FK 1:1, obrigatoriedade e unicidades de `usuario`;
- `V3__administracao_segura_usuario.sql`: adiciona `usuario.ativo BOOLEAN NOT NULL DEFAULT TRUE` e `usuario.token_version BIGINT NOT NULL DEFAULT 0`, preservando as contas existentes;
- `V4__matricula_contas_receber.sql`: torna obrigatórios os vínculos financeiros e impede recebimento duplicado e duas cobranças do mesmo aluno no mesmo vencimento;
- `V5__chamados_manutencao.sql`: amplia a descrição para 2.000, adiciona observação administrativa, obrigatoriedade dos campos, validação de descrição não vazia e índices; mantém `responsavel_id` e as FKs existentes. Falha com mensagem clara se houver chamado legado incompleto, sem apagar registros nem inventar autor;
- `V6__notificacoes_whatsapp.sql`: adiciona somente configuração, assinatura, lote e entrega, com automático desligado, unicidades, índices da fila e FKs CASCADE/SET NULL para preservar histórico sem impedir exclusão de acesso. Não altera datas, pessoas ou contas existentes;
- em um PostgreSQL antigo sem histórico Flyway, `baseline-on-migrate=true` registra a versão 1 e aplica as evoluções posteriores;
- depois das migrações, Hibernate usa `ddl-auto=validate` e falha se o mapeamento divergir do banco.

Antes de executar a migração em um ambiente importante, faça backup do banco. Os registros existentes de `pessoa` não são apagados.

### Configuração opcional do WhatsApp

Sem WHATSAPP_ENABLED=true, não são enviados documentos; o restante da aplicação inicia normalmente sem credenciais Meta. As variáveis externas são WHATSAPP_GRAPH_API_VERSION, WHATSAPP_ACCESS_TOKEN, WHATSAPP_WABA_ID, WHATSAPP_PHONE_NUMBER_ID, WHATSAPP_BUSINESS_NUMBER, WHATSAPP_APP_SECRET, WHATSAPP_VERIFY_TOKEN e WHATSAPP_TEMPLATE_NAME. Modo de teste padrão exige WHATSAPP_TEST_ALLOWED_NUMBERS; produção é habilitada explicitamente com WHATSAPP_PRODUCTION=true. WHATSAPP_SCHEDULERS_ENABLED controla as rotinas do módulo e fica false nos testes.

Requisitos para envio real: conta/número Meta, template DOCUMENT aprovado em pt_BR com três parâmetros, webhook HTTPS verificado e destinatários que confirmaram o consentimento. O guia específico detalha a configuração. Não existe provisionamento automático de conta Meta nem promessa de gratuidade.

---

## 12. Testes e tratamento de erros

Os testes usam H2 em modo PostgreSQL, Flyway desabilitado e `ddl-auto=create-drop`, sem depender do banco local. A suíte cobre carregamento do contexto, bootstrap único, vínculo usuário-pessoa, derivação dos papéis, conflitos, BCrypt, login, claims e duração do JWT, tokens expirado/adulterado/sem versão, matriz `200/401/403`, isolamento das viagens do motorista, CORS, ausência de senha/versão nas respostas, idempotência, revogação após alterações, conta inativa, proteções administrativas, preservação de `Pessoa`, primeira cobrança da matrícula, recorrência sem duplicidade, pagamento, recibo, propriedade da conta e validação da mensalidade.

Os testes de manutenção cobrem autoria automática e campos protegidos, permissões, isolamento, filtros, criação por aluno/motorista, tratamento de dados inválidos, atendimento, reabertura, independência entre estados, preservação dos dados do ônibus, histórico após excluir acesso, bloqueio de exclusão física e disponibilidade nas viagens/grades.

`DashboardIntegrationTests` usa relógio controlado para testar permissões HTTP e nos serviços, isolamento por faculdade/motorista, múltiplos destinos, rotas sem viagens, deduplicação dos pontos, dados incompletos, privacidade das respostas, agenda semanal/virada de semana/fuso, datas legadas ignoradas, programação indefinida e alertas de indisponibilidade. Também cobre prioridade das cobranças, situações/dias/totais, atualização após pagamento, períodos e agregações financeiras sem duplicação, saldo negativo, indicadores, cinco pendências, ausência de dados e preservação de registros após consultas.

`ManutencaoPostgresMigrationTests` é opcional, habilitado por `MANUTENCAO_TEST_DB_URL`, com usuário/senha externos em `MANUTENCAO_TEST_DB_USER` e `MANUTENCAO_TEST_DB_PASSWORD`. Valida V1–V5 em schema vazio, atualização V4→V5 com registros preservados, FKs/obrigatoriedade/limites e falha segura para chamado incompleto. Cria e remove somente schemas temporários próprios `test_manutencao_*`, sem alterar o schema público.

`DashboardPostgresIntegrationTests` repete os 15 testes do dashboard contra PostgreSQL real quando `DASHBOARD_TEST_DB_URL`, `DASHBOARD_TEST_DB_USER` e `DASHBOARD_TEST_DB_PASSWORD` são informados ao processo Maven. Aplica as migrations atuais e valida o schema em um schema temporário próprio `test_dashboard_*`, removido ao final. Sem essas variáveis, essa suíte é ignorada; os 15 testes H2 continuam executando. Não usa nem modifica as tabelas públicas do sistema.

`WhatsappIntegrationTests` usa banco H2 próprio, relógio controlado e gateway Meta simulado. Cobre consentimento, confirmação, expiração, limite de ativações, cancelamento, número único, lista de teste, assinatura/WABA/número do webhook, permissões, agenda, snapshot, idempotência, revisões, fila/HTTP fora de transação, concorrência, conta excluída/inativa, atrasos, tentativas e estados fora de ordem. `MetaWhatsappClientTests` verifica contratos HTTP/multipart/template, media ID/correlação e classificação de falhas. `ProgramacaoPdfTests` gera exemplos em target/whatsapp-pdf e valida acentos, fonte embutida, texto longo, paginação e ausência de PDF vazio; as renderizações também foram inspecionadas visualmente.

`WhatsappPostgresIntegrationTests` repete a suíte contra V1–V6 em schema temporário `test_whatsapp_*`. `WhatsappPostgresMigrationTests` testa atualização V5→V6 com contas/datas preservadas, assinatura removida na exclusão de acesso e histórico/Pessoa mantidos. Ambas são opcionais, habilitadas por WHATSAPP_TEST_DB_URL, WHATSAPP_TEST_DB_USER e WHATSAPP_TEST_DB_PASSWORD. Nenhuma suíte usa a Meta real ou altera tabelas públicas.

O `RestExceptionHandler` padroniza as falhas de autenticação, validação, formato inválido, inexistência, conflito e integridade com `ProblemDetail`. `ViagemController` e `GradeDiariaController` mantêm handlers locais legados para `RuntimeException`, mas possuem handlers específicos `409/404` para atribuição de ônibus. Recibos continuam retornando `404` por `ResponseEntity` quando não encontrados.

---

## 13. Limitações e pontos de atenção identificados

1. As credenciais do PostgreSQL ainda estão em texto simples no repositório.
2. `ATIV0` e `INATIV0` usam zero e podem causar integração incorreta.
3. `Rota` e `PontoEmbarque` modelam a relação de duas formas independentes: N:M de um lado e N:1 do outro.
4. `GradeDiariaDTO.data` não é persistido nem preenchido nas respostas.
5. `ViagemDTO` pode omitir faculdades quando a viagem não possui motorista.
6. O DTO de aluno aceita mensalidade, mas status e data de cadastro continuam controlados pelo serviço.
7. O DTO de motorista não aceita tipo de pessoa; o serviço o define automaticamente como `MOTORISTA`.
8. A capacidade da viagem usa todos os alunos `ATIV0` das faculdades, pois não existe vínculo aluno-viagem.
9. Pagamento parcial marca a conta integralmente como `PAGO`.
10. `PagamentoRepository.findByContaPagarId` espera um resultado, embora o modelo não imponha unicidade.
11. Pessoa possui camadas internas, mas não API própria; manutenção, contas a receber e recebimentos expõem fluxos específicos, sem CRUD genérico completo.
12. A validação Bean Validation foi adicionada aos contratos novos de autenticação e usuários, mas os DTOs CRUD antigos ainda não possuem validação completa.
13. O JWT não possui refresh token nem lista geral de bloqueio; a revogação ocorre por versão da conta e exige uma consulta ao banco em cada requisição autenticada. O logout comum apenas remove a sessão do frontend.
14. O uso planejado de `sessionStorage` no Angular é simples, mas exige prevenção rigorosa de XSS.
15. Alguns endpoints antigos retornam entidades JPA e usam tratamento de erro não padronizado.
16. Manutenção não possui chat, anexos, notificações, oficina, cobrança ou histórico de alterações da observação; a última gravação administrativa prevalece.
17. Ônibus indisponível não recebe novas atribuições, mas viagens existentes não são canceladas/remanejadas automaticamente.
18. Datas legadas de viagem continuam nos contratos antigos por compatibilidade; somente a grade/dia determina a agenda do dashboard.
19. O dashboard mostra transporte da faculdade, não uma atribuição individual de ônibus ou ponto ao aluno; não há horários ou rastreamento.
20. Dashboards de transporte retornam todas as atribuições pertinentes, sem paginação de servidor; bases grandes podem exigir paginação futura.
21. WhatsApp depende de configuração/aprovação da Meta, webhook público HTTPS e custos/regras da plataforma; testes locais não comprovam entrega real.
22. A fila prioriza evitar duplicidade: resultado incerto exige verificação administrativa, sem promessa de entrega exatamente uma vez.
23. Histórico de WhatsApp mantém snapshots e PDFs no banco; não há política automática de retenção/remoção nesta versão.

---

## 14. Estado da revisão

- **Última revisão:** 14 de outubro de 2026
- **Versão do projeto:** `0.0.1-SNAPSHOT`
- **Status:** em desenvolvimento
- **Fonte:** implementação atual do repositório
