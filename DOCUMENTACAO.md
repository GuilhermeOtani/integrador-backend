# 📋 Documentação - Projeto Integrador 2026

## 📌 Visão Geral

O **Projeto Integrador** é uma aplicação backend desenvolvida em **Spring Boot 4.0.3** com **Java 21** que gerencia um sistema completo de transporte escolar/universitário. A aplicação permite o gerenciamento integrado de alunos, ônibus, motoristas, rotas, viagens, pontos de embarque com ordem de parada e controle financeiro completo.

**Atualização**: Maio 2026 - Documentação totalmente sincronizada com o código-fonte

---

## 🎯 Funcionalidades Principais

### 1. **Gerenciamento de Alunos**
- Cadastro e atualização de alunos com validação via AlunoDTO
- Controle de status de matrícula (ATIVA, INATIVA, PENDENTE)
- Associação de alunos a faculdades, rotas e viagens
- Vinculação bidirecional com viagens
- Listagem e busca de alunos

### 2. **Gerenciamento de Ônibus**
- Cadastro de ônibus com identificação, placa e modelo
- Controle de status (ATIVO, INATIVO, EM_MANUTENCAO)
- Armazenamento de capacidade e fotos (LONGTEXT)
- Acompanhamento de manutenção com chamados

### 3. **Gerenciamento de Motoristas**
- Cadastro com validação via MotoristaDTO
- CNH e salário (BigDecimal)
- Associação exclusiva a um ônibus
- Realização de múltiplas viagens
- Controle de contas a pagar

### 4. **Rotas e Viagens**
- Criação e gerenciamento de rotas
- Associação de pontos de embarque com **ordem de parada** (sequência)
- Registro de viagens com data específica
- Controle de motoristas e alunos em viagens
- Grade diária de viagens (seg-sex)
- Vinculação bidirecional entre Viagem e Aluno

### 5. **Sistema Financeiro Integrado**
- **Contas a Pagar**: Despesas com Pessoa (motorista/outro) em BigDecimal
- **Contas a Receber**: Receitas de alunos com descrição
- **Pagamentos**: Transações realizadas em BigDecimal
- **Recebimentos**: Registro separado para recebimentos
- **Formas de Pagamento**: PIX, Boleto, Dinheiro, Cartão Crédito/Débito, Transferência
- **Status Financeiro**: PENDENTE, PAGO, VENCIDO, CANCELADO

### 6. **Manutenção de Ônibus**
- Registro de chamados de manutenção
- Níveis de urgência: BAIXA, MEDIA, ALTA, CRITICA
- Status: ABERTO, EM_ANDAMENTO, CONCLUIDO, CANCELADO
- Designação de responsável (Pessoa)
- Data de abertura do chamado

### 7. **Segurança e Autenticação**
- Spring Security com UserDetails (Usuario)
- Autenticação via Email/Senha
- Tipos de Pessoa: ALUNO, MOTORISTA, ADMINISTRADOR, GERENTE_FINANCEIRO, OPERADOR
- CORS configurado para integração com frontend
- Criptografia de senhas

### 8. **Funcionalidades Adicionais**
- Cadastro de faculdades (sem telefone)
- Pontos de embarque simplificados (ordem de parada)
- Usuários separados com email e senha
- Agendamento de tarefas (@EnableScheduling)
- DTOs para validação (AlunoDTO, MotoristaDTO, ReciboDTO)

---

## 🏗️ Arquitetura

### Padrão de Camadas

```
┌─────────────────────────────────────────┐
│          Controller (REST API)          │
│  (Endpoints HTTP para integração)       │
└────────────────────┬────────────────────┘
                     │
┌────────────────────▼────────────────────┐
│         Service (Lógica de Negócio)     │
│  (Processamento e regras de negócio)    │
└────────────────────┬────────────────────┘
                     │
┌────────────────────▼────────────────────┐
│        Repository (Acesso a Dados)      │
│  (Queries ao banco PostgreSQL via JPA)  │
└────────────────────┬────────────────────┘
                     │
┌────────────────────▼────────────────────┐
│       Banco de Dados (PostgreSQL)       │
└─────────────────────────────────────────┘
```

### Estrutura de Pacotes

```
com.example.integrador/
├── Controller/          # Endpoints REST
├── Service/             # Lógica de negócio
├── Repository/          # Acesso a dados
├── Model/               # Entidades JPA
├── DTO/                 # Data Transfer Objects
├── Enum/                # Enumerações
├── Security/            # Configurações de segurança
└── Config/              # Configurações gerais
```

---

## 🗄️ Modelos de Dados (Entidades)

### **Pessoa** (Classe Base - Abstract)
- `id`: Identificador único (Long)
- `nome`: Nome da pessoa (String)
- `cpfCnpj`: CPF ou CNPJ (String) [⚠️ MUDANÇA: era `email`]
- `telefone`: Telefone (String)
- `tipoPessoa`: Tipo de pessoa - Enum TipoPessoa

**Tipos**: ALUNO, MOTORISTA, ADMINISTRADOR, GERENTE_FINANCEIRO, OPERADOR

**Subclasses**: Aluno, Motorista

**Relacionamentos**:
- 1:N com ContaPagar
- 1:N com ChamadoManutencao

---

### **Aluno** (Estende Pessoa)
- `matricula`: Número de matrícula (String)
- `statusMatricula`: Enum StatusMatricula (ATIVA, INATIVA, PENDENTE)
- `dataCadastro`: Data de cadastro (LocalDate)
- `faculdade`: Faculdade associada (N:1)
- `rota`: Rota utilizada (N:1)
- `viagem`: Viagem vinculada (N:1) [⚠️ NOVO]

**Relacionamentos**:
- N:1 com Faculdade
- N:1 com Rota
- N:1 com Viagem [⚠️ NOVO]
- 1:N com ContaReceber

---

### **Motorista** (Estende Pessoa)
- `cnh`: Número da CNH (String)
- `salario`: Salário (BigDecimal)
- `onibus`: Ônibus que opera (N:1)

**Relacionamentos**:
- N:1 com Onibus
- 1:N com Viagem

---

### **Usuario** (Entidade Separada)
- `id`: Identificador único (Long)
- `email`: Email para autenticação (String)
- `senha`: Senha criptografada (String)
- `tipoPessoa`: Tipo de pessoa - Enum TipoPessoa

**Implementa**: UserDetails (Spring Security)

---

### **Faculdade**
- `id`: Identificador único (Long)
- `nome`: Nome (String)
- `endereco`: Endereço (String)

[⚠️ REMOVIDO: `telefone`]

**Relacionamentos**:
- 1:N com Aluno
- 1:N com Rota

---

### **Onibus**
- `id`: Identificador único (Long)
- `numeroIdentificacao`: ID único (String, Unique)
- `placa`: Placa do veículo (String)
- `modelo`: Modelo (String)
- `statusOnibus`: Enum StatusOnibus (ATIVO, INATIVO, EM_MANUTENCAO)
- `capacidade`: Capacidade em passageiros (Double)
- `fotoUrl`: URL da foto em LONGTEXT (String)

**Relacionamentos**:
- 1:N com Motorista
- 1:N com Viagem
- 1:N com ChamadoManutencao

---

### **Rota**
- `id`: Identificador único (Long)
- `nome`: Nome da rota (String)
- `descricao`: Descrição (String)
- `faculdade`: Faculdade associada (N:1)
- `pontosEmbarque`: Lista de pontos (N:M)

**Relacionamentos**:
- N:1 com Faculdade
- N:M com PontoEmbarque
- 1:N com Aluno
- 1:N com Viagem

---

### **PontoEmbarque**
- `id`: Identificador único (Long)
- `ordemParada`: Ordem de parada na rota (Integer)

[⚠️ MUDANÇA DRÁSTICA: Removidos `nome`, `endereco`, `coordenadas`]

**Relacionamentos**:
- N:M com Rota

---

### **Viagem**
- `id`: Identificador único (Long)
- `data`: Data da viagem (LocalDate)
- `onibus`: Ônibus utilizado (N:1)
- `rota`: Rota percorrida (N:1)
- `motorista`: Motorista responsável (N:1)
- `gradeDiaria`: Grade diária (N:1)
- `alunos`: Lista de alunos (1:N) [⚠️ NOVO]

**Relacionamentos**:
- N:1 com Onibus
- N:1 com Rota
- N:1 com Motorista
- N:1 com GradeDiaria
- 1:N com Aluno [⚠️ NOVO]

---

### **GradeDiaria**
- `id`: Identificador único (Long)
- `data`: Data da grade (LocalDate)
- `diaSemana`: Enum DiaSemana (SEGUNDA, TERCA, QUARTA, QUINTA, SEXTA)
- `descricao`: Descrição (String)
- `viagens`: Lista de viagens (1:N)

**Relacionamentos**:
- 1:N com Viagem

---

### **ContaPagar**
- `id`: Identificador único (Long)
- `descricao`: Descrição (String)
- `valor`: Valor (BigDecimal)
- `dataVencimento`: Data de vencimento (LocalDate)
- `status`: Enum StatusFinanceiro (PENDENTE, PAGO, VENCIDO, CANCELADO)
- `motorista`: Pessoa associada (N:1) [⚠️ MUDANÇA: agora é `Pessoa` genérica]

**Relacionamentos**:
- N:1 com Pessoa
- 1:N com Pagamento

---

### **ContaReceber**
- `id`: Identificador único (Long)
- `valor`: Valor (Double)
- `descricao`: Descrição [⚠️ NOVO]
- `dataVencimento`: Data de vencimento (LocalDate)
- `status`: Enum StatusFinanceiro (PENDENTE, PAGO, VENCIDO, CANCELADO)
- `aluno`: Aluno devedor (N:1)

**Relacionamentos**:
- N:1 com Aluno
- 1:N com Recebimento

---

### **Pagamento**
- `id`: Identificador único (Long)
- `valor`: Valor (BigDecimal)
- `dataPagamento`: Data do pagamento (LocalDate)
- `formaPagamento`: Enum FormaPagamento
- `contaPagar`: Conta a pagar (N:1)

[⚠️ REMOVIDO: Relacionamento com `ContaReceber`]

**Relacionamentos**:
- N:1 com ContaPagar

---

### **Recebimento**
- `id`: Identificador único (Long)
- `valor`: Valor recebido (Double)
- `dataRecebimento`: Data do recebimento (LocalDate)
- `formaPagamento`: Enum FormaPagamento (PIX, BOLETO, DINHEIRO, CARTAO_CREDITO, CARTAO_DEBITO, TRANSFERENCIA)
- `contaReceber`: Conta a receber (N:1)

**Relacionamentos**:
- N:1 com ContaReceber

---

### **ChamadoManutencao**
- `id`: Identificador único (Long)
- `descricao`: Descrição do problema (String)
- `dataAbertura`: Data de abertura (LocalDate) [⚠️ MUDANÇA: era `dataCriacao`]
- `urgencia`: Enum NivelUrgencia (BAIXA, MEDIA, ALTA, CRITICA) [⚠️ MUDANÇA: era `nivelUrgencia`]
- `status`: Enum StatusChamado (ABERTO, EM_ANDAMENTO, CONCLUIDO, CANCELADO)
- `onibus`: Ônibus em manutenção (N:1)
- `responsavel`: Pessoa responsável (N:1) [⚠️ NOVO]

[⚠️ REMOVIDO: `dataResolucao`]

**Relacionamentos**:
- N:1 com Onibus
- N:1 com Pessoa

---

## 🔌 Endpoints Principais

### Alunos
```
GET    /aluno/listar                    # Listar todos os alunos
GET    /aluno/buscar-aluno/{id}         # Buscar aluno por ID
POST   /aluno/salvar-aluno              # Criar novo aluno (requer AlunoDTO)
PUT    /aluno/atualizar-aluno/{id}      # Atualizar aluno (requer AlunoDTO)
DELETE /aluno/deletar-aluno/{id}        # Deletar aluno
```

### Ônibus
```
GET    /onibus/listar                   # Listar todos os ônibus
GET    /onibus/buscar-onibus/{id}       # Buscar ônibus por ID
POST   /onibus/salvar-onibus            # Criar novo ônibus
PUT    /onibus/atualizar-onibus/{id}    # Atualizar ônibus
DELETE /onibus/deletar-onibus/{id}      # Deletar ônibus
```

### Motoristas
```
GET    /motorista/listar                # Listar todos os motoristas
GET    /motorista/buscar-motorista/{id} # Buscar motorista por ID
POST   /motorista/salvar-motorista      # Criar novo motorista (requer MotoristaDTO)
PUT    /motorista/atualizar-motorista/{id} # Atualizar motorista
DELETE /motorista/deletar-motorista/{id}   # Deletar motorista
```

### Contas a Pagar
```
GET    /contapagar/listar               # Listar contas a pagar
GET    /contapagar/buscar-contapagar/{id} # Buscar conta por ID
POST   /contapagar/salvar-contapagar    # Criar conta a pagar
PUT    /contapagar/atualizar-contapagar/{id} # Atualizar conta
DELETE /contapagar/deletar-contapagar/{id}  # Deletar conta
```

### Faculdades
```
GET    /faculdade/listar                # Listar faculdades
GET    /faculdade/buscar-faculdade/{id} # Buscar faculdade por ID
POST   /faculdade/salvar-faculdade      # Criar faculdade
PUT    /faculdade/atualizar-faculdade/{id} # Atualizar faculdade
DELETE /faculdade/deletar-faculdade/{id}   # Deletar faculdade
```

### Pagamentos
```
GET    /pagamento/listar                # Listar pagamentos
GET    /pagamento/buscar-pagamento/{id} # Buscar pagamento por ID
POST   /pagamento/salvar-pagamento      # Criar pagamento
PUT    /pagamento/atualizar-pagamento/{id} # Atualizar pagamento
DELETE /pagamento/deletar-pagamento/{id}   # Deletar pagamento
```

---

## 📦 Data Transfer Objects (DTOs)

### **AlunoDTO**
- `id`: ID do aluno
- `nome`: Nome completo
- `cpfCnpj`: CPF do aluno
- `telefone`: Telefone
- `matricula`: Número de matrícula
- `faculdadeId`: ID da faculdade

### **MotoristaDTO**
- `id`: ID do motorista
- `nome`: Nome completo
- `cpfCnpj`: CPF do motorista
- `cnh`: Número da CNH
- `salario`: Salário (BigDecimal)
- `telefone`: Telefone
- `onibusId`: ID do ônibus
- `numeroIdentificacao`: Número de identificação do ônibus

### **ReciboDTO**
- `id`: Identificador único do recibo (Long)
- `valor`: Valor efetivamente pago (BigDecimal)
- `dataPagamento`: Data de realização do pagamento (LocalDate)
- `formaPagamento`: Método utilizado (String)
- `nomeMotorista`: Nome da pessoa favorecida (String)
- `descricaoConta`: Referência do que está sendo pago (String)
- `dataVencimento`: Data de vencimento original da conta (LocalDate)

---

## 📊 Enumerações

### StatusMatricula
- ATIVA
- INATIVA
- PENDENTE

### DiaSemana
- SEGUNDA
- TERCA
- QUARTA
- QUINTA
- SEXTA

### StatusOnibus
- ATIVO
- INATIVO
- EM_MANUTENCAO

### StatusFinanceiro
- PENDENTE
- PAGO
- VENCIDO
- CANCELADO

### StatusChamado
- ABERTO
- EM_ANDAMENTO
- CONCLUIDO
- CANCELADO

### NivelUrgencia
- BAIXA
- MEDIA
- ALTA
- CRITICA

### FormaPagamento
- PIX
- BOLETO
- DINHEIRO
- CARTAO_CREDITO
- CARTAO_DEBITO
- TRANSFERENCIA

### TipoPessoa
- ALUNO
- MOTORISTA
- ADMINISTRADOR
- GERENTE_FINANCEIRO
- OPERADOR

---

## ⚙️ Configurações Técnicas

### Stack Tecnológico

| Tecnologia | Versão | Propósito |
|-----------|--------|----------|
| Spring Boot | 4.0.3 | Framework principal |
| Java | 21 | Linguagem de programação |
| Spring Security | Latest | Autenticação e autorização |
| Spring Data JPA | Latest | Persistência de dados |
| PostgreSQL | Latest | Banco de dados relacional |
| Hibernate | Latest | ORM |
| Lombok | Latest | Redução de boilerplate |

### Banco de Dados
- **SGBD**: PostgreSQL
- **Database**: integrador
- **Porta**: 5432

### Aplicação
- **Porta**: 8080
- **URL Base**: http://localhost:8080

---

## 🔐 Segurança

- Spring Security com UserDetails
- Autenticação via Email/Senha
- Criptografia de senhas
- CORS configurado
- Tipos de acesso por TipoPessoa

---

## 🚀 Como Executar

1. Configure PostgreSQL (banco `integrador`)
2. Execute: `mvn clean install`
3. Execute: `mvn spring-boot:run`
4. Acesse: `http://localhost:8080`

---

**Última Atualização**: Maio 2026  
**Versão**: 1.0  
**Status**: Em Desenvolvimento
