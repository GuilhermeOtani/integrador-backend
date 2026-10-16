package com.example.integrador.Service;

import com.example.integrador.Enum.*;
import com.example.integrador.Model.*;
import com.example.integrador.DTO.ContaPagarResponse;
import com.example.integrador.Repository.*;
import com.example.integrador.Exception.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;

@Service
public class ContaPagarService {
    private final ContaPagarRepository contas;
    private final PagamentoRepository pagamentos;
    private final PessoaBloqueioService bloqueios;
    private final PosicaoContaPagarRepository posicoes;
    private final EntityManager em;
    private final Clock clock;
    public ContaPagarService(ContaPagarRepository contas, PagamentoRepository pagamentos, PessoaBloqueioService bloqueios,
                            PosicaoContaPagarRepository posicoes, EntityManager em, Clock clock) {
        this.contas = contas; this.pagamentos = pagamentos; this.bloqueios = bloqueios;
        this.posicoes = posicoes; this.em = em; this.clock = clock;
    }

    public List<ContaPagar> listarTodasContaPagar() { return contas.findAll(); }

    @Transactional(readOnly = true)
    public List<ContaPagarResponse> listarRespostas() {
        var lista = contas.listarComPessoa();
        var saldos = posicoes.calcular(lista);
        return lista.stream().map(c -> ContaPagarResponse.from(c, saldos.get(c.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public ContaPagarResponse buscarResposta(Long id) { return resposta(buscarContaPagarPorId(id)); }

    @Transactional
    public ContaPagarResponse salvarResposta(ContaPagar entrada) { return resposta(salvarContaPagar(entrada)); }

    @Transactional
    public ContaPagarResponse atualizarResposta(Long id, ContaPagar entrada) { return resposta(atualizarContaPagar(id, entrada)); }

    private ContaPagarResponse resposta(ContaPagar conta) {
        return ContaPagarResponse.from(conta, posicoes.calcular(List.of(conta)).get(conta.getId()));
    }

    @Transactional
    public ContaPagar salvarContaPagar(ContaPagar conta) {
        if (conta.getId() != null) return atualizarContaPagar(conta.getId(), conta);
        Long pessoaId = pessoaId(conta);
        if (pessoaId != null) {
            Pessoa pessoa = bloqueios.bloquearPessoa(pessoaId);
            bloqueios.exigirNaoArquivado(pessoa);
            conta.setMotorista(pessoa);
        }
        preparar(conta);
        if (conta.getStatus() == StatusFinanceiro.PAGO)
            throw new RegraNegocioException("Registre o pagamento para quitar a conta.");
        if (conta.getStatus() == null) conta.setStatus(StatusFinanceiro.PENDENTE);
        validarSalarioUnico(conta);
        return contas.saveAndFlush(conta);
    }

    public ContaPagar buscarContaPagarPorId(Long id) {
        return contas.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Conta a pagar não encontrada."));
    }

    @Transactional
    public void deletarContaPagarPorId(Long id) {
        ContaPagar conta = contas.findLockedById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Conta a pagar não encontrada."));
        em.refresh(conta, LockModeType.PESSIMISTIC_WRITE);
        if (pagamentos.existsByContaPagarId(id) || conta.getStatus() == StatusFinanceiro.PAGO)
            throw new ConflitoException("Uma conta com pagamentos ou quitação histórica não pode ser excluída.");
        contas.delete(conta);
    }

    @Transactional
    public ContaPagar atualizarContaPagar(Long id, ContaPagar entrada) {
        ContaPagar conta = buscarContaPagarPorId(id);
        Long anterior = pessoaId(conta), destino = pessoaId(entrada);
        bloqueios.bloquearPessoasEmOrdem(Stream.of(anterior, destino).filter(Objects::nonNull).toList());
        Pessoa pessoa = destino == null ? null : bloqueios.bloquearPessoa(destino);
        if (pessoa != null && !Objects.equals(anterior, destino)) bloqueios.exigirNaoArquivado(pessoa);
        em.refresh(conta, LockModeType.PESSIMISTIC_WRITE);
        if (!Objects.equals(pessoaId(conta), anterior)) throw new ConflitoException("O proprietário da conta mudou. Atualize e tente novamente.");
        boolean temPagamento = pagamentos.existsByContaPagarId(id);
        boolean quitadaLegada = conta.getStatus() == StatusFinanceiro.PAGO && !temPagamento;
        boolean valorAlterado = conta.getValor() == null ? entrada.getValor() != null : entrada.getValor() == null || conta.getValor().compareTo(entrada.getValor()) != 0;
        if ((temPagamento || quitadaLegada) && (valorAlterado || !Objects.equals(anterior, destino)))
            throw new ConflitoException("Valor e beneficiário não podem mudar após um pagamento ou quitação histórica.");
        if (entrada.getStatus() != null && entrada.getStatus() != conta.getStatus())
            throw new RegraNegocioException("O status financeiro é atualizado pelo registro de pagamentos.");
        conta.setDescricao(entrada.getDescricao()); conta.setValor(entrada.getValor());
        conta.setMotorista(pessoa); conta.setDataVencimento(entrada.getDataVencimento());
        if (entrada.getClassificacao() != null) conta.setClassificacao(entrada.getClassificacao());
        if (entrada.getCompetencia() != null) conta.setCompetencia(entrada.getCompetencia());
        preparar(conta);
        validarSalarioUnico(conta);
        return contas.saveAndFlush(conta);
    }

    private void preparar(ContaPagar conta) {
        if (conta.getClassificacao() == null) conta.setClassificacao(ClassificacaoConta.DESPESA);
        if (conta.getClassificacao() == ClassificacaoConta.SALARIO) {
            if (!(conta.getMotorista() instanceof Motorista)) throw new RegraNegocioException("Salário deve estar vinculado a um motorista.");
            if (conta.getCompetencia() == null || conta.getDataVencimento() == null)
                throw new RegraNegocioException("Informe competência e vencimento do salário.");
            if (conta.getDiaVencimentoSalario() == null) conta.setDiaVencimentoSalario(conta.getDataVencimento().getDayOfMonth());
        } else if (conta.getCompetencia() == null && conta.getDataVencimento() != null)
            conta.setCompetencia(YearMonth.from(conta.getDataVencimento()).atDay(1));
        if (conta.getCompetencia() != null && conta.getCompetencia().getDayOfMonth() != 1)
            throw new RegraNegocioException("A competência deve representar o primeiro dia do mês.");
    }

    private void validarSalarioUnico(ContaPagar conta) {
        if (conta.getClassificacao() != ClassificacaoConta.SALARIO) return;
        // Todos os escritores de salário possuem o lock da pessoa antes de consultar.
        String jpql = "select count(c) from ContaPagar c where c.motorista.id = :pessoa and c.classificacao = :tipo and c.competencia = :mes";
        if (conta.getId() != null) jpql += " and c.id <> :id";
        var query = em.createQuery(jpql, Long.class).setParameter("pessoa", conta.getMotorista().getId())
                .setParameter("tipo", ClassificacaoConta.SALARIO).setParameter("mes", conta.getCompetencia());
        if (conta.getId() != null) query.setParameter("id", conta.getId());
        Long repetidas = query.getSingleResult();
        if (repetidas > 0) throw new ConflitoException("Já existe salário para este motorista e competência.");
    }

    private Long pessoaId(ContaPagar conta) {
        if (conta.getMotorista() == null) return null;
        if (conta.getMotorista().getId() == null) throw new RegraNegocioException("Informe o ID da pessoa vinculada.");
        return conta.getMotorista().getId();
    }

    @Transactional
    public void gerarPrimeiraConta(Motorista referencia) {
        Motorista motorista = (Motorista) bloqueios.bloquearPessoa(referencia.getId());
        LocalDate vencimento = LocalDate.now(clock).plusDays(30);
        ContaPagar conta = salario(motorista, vencimento, YearMonth.from(vencimento).atDay(1), vencimento.getDayOfMonth());
        salvarContaPagar(conta);
    }

    @Transactional
    public void gerarSalarioRecorrente(Long motoristaId) {
        Motorista motorista = (Motorista) bloqueios.bloquearPessoa(motoristaId);
        ContaPagar ultima = contas.findFirstByMotoristaIdAndClassificacaoOrderByCompetenciaDescDataVencimentoDescIdDesc(motoristaId, ClassificacaoConta.SALARIO);
        if (ultima == null || ultima.getDataVencimento() == null || ultima.getCompetencia() == null || ultima.getDataVencimento().isAfter(LocalDate.now(clock))) return;
        int dia = ultima.getDiaVencimentoSalario() == null ? ultima.getDataVencimento().getDayOfMonth() : ultima.getDiaVencimentoSalario();
        YearMonth proximoVencimento = YearMonth.from(ultima.getDataVencimento()).plusMonths(1);
        LocalDate competencia = ultima.getCompetencia().plusMonths(1);
        if (contas.existsByMotoristaIdAndClassificacaoAndCompetencia(motoristaId, ClassificacaoConta.SALARIO, competencia)) return;
        salvarContaPagar(salario(motorista, proximoVencimento.atDay(Math.min(dia, proximoVencimento.lengthOfMonth())), competencia, dia));
    }

    private ContaPagar salario(Motorista motorista, LocalDate vencimento, LocalDate competencia, int dia) {
        ContaPagar conta = new ContaPagar(); conta.setMotorista(motorista);
        conta.setDescricao("Salário - " + motorista.getNome()); conta.setValor(motorista.getSalario());
        conta.setStatus(StatusFinanceiro.PENDENTE); conta.setClassificacao(ClassificacaoConta.SALARIO);
        conta.setCompetencia(competencia); conta.setDataVencimento(vencimento); conta.setDiaVencimentoSalario(dia);
        return conta;
    }
}
