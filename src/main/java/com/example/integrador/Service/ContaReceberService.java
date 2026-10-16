package com.example.integrador.Service;

import com.example.integrador.DTO.ContaReceberResponse;
import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Exception.*;
import com.example.integrador.Model.*;
import com.example.integrador.Repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class ContaReceberService {
    private final ContaReceberRepository contas;
    private final RecebimentoRepository recebimentos;
    private final PosicaoContaReceberRepository posicoes;
    private final PessoaBloqueioService bloqueios;
    private final EntityManager em;
    private final Clock clock;
    public ContaReceberService(ContaReceberRepository contas, RecebimentoRepository recebimentos,
            PosicaoContaReceberRepository posicoes, PessoaBloqueioService bloqueios, EntityManager em, Clock clock) {
        this.contas = contas; this.recebimentos = recebimentos; this.posicoes = posicoes;
        this.bloqueios = bloqueios; this.em = em; this.clock = clock;
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<ContaReceberResponse> listarTodasContaReceber() { return respostas(contas.listarComAluno()); }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ALUNO')")
    public List<ContaReceberResponse> listarMinhasContas(Long alunoId) {
        return respostas(contas.findAllByAlunoIdOrderByDataVencimentoDesc(alunoId));
    }
    private List<ContaReceberResponse> respostas(List<ContaReceber> lista) {
        var saldos = posicoes.calcular(lista);
        return lista.stream().map(c->ContaReceberResponse.fromEntity(c, saldos.get(c.getId()))).toList();
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public ContaReceberResponse buscarResposta(Long id) { return respostas(List.of(buscarContaReceberPorId(id))).getFirst(); }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ContaReceberResponse salvarResposta(ContaReceber c) { return respostas(List.of(salvarContaReceber(c))).getFirst(); }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ContaReceberResponse atualizarResposta(Long id, ContaReceber c) { return respostas(List.of(atualizarContaReceber(id, c))).getFirst(); }

    @Transactional
    public ContaReceber salvarContaReceber(ContaReceber conta) {
        if (conta.getId() != null) return atualizarContaReceber(conta.getId(), conta);
        Aluno aluno = bloqueios.bloquearAluno(alunoId(conta)); bloqueios.exigirNaoArquivado(aluno); conta.setAluno(aluno);
        preparar(conta);
        if (conta.getStatus() == StatusFinanceiro.PAGO) throw new RegraNegocioException("Registre o recebimento para quitar a cobrança.");
        if (conta.getStatus() == null) conta.setStatus(StatusFinanceiro.PENDENTE);
        validarCompetenciaUnica(conta);
        return contas.saveAndFlush(conta);
    }
    public ContaReceber buscarContaReceberPorId(Long id) {
        return contas.findById(id).orElseThrow(()->new RecursoNaoEncontradoException("Conta a receber não encontrada."));
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deletarContaReceberPorId(Long id) {
        Long alunoId = contas.findAlunoIdById(id).orElseThrow(()->new RecursoNaoEncontradoException("Conta a receber não encontrada."));
        bloqueios.bloquearAluno(alunoId);
        ContaReceber conta = contas.findLockedById(id).orElseThrow(()->new RecursoNaoEncontradoException("Conta a receber não encontrada."));
        em.refresh(conta, LockModeType.PESSIMISTIC_WRITE);
        if (!conta.getAluno().getId().equals(alunoId)) throw new ConflitoException("O proprietário da cobrança mudou. Atualize e tente novamente.");
        if (recebimentos.existsByContaReceberId(id) || conta.getStatus() == StatusFinanceiro.PAGO)
            throw new ConflitoException("Cobranças com recebimentos ou quitação histórica não podem ser excluídas.");
        contas.delete(conta);
    }
    @Transactional
    public ContaReceber atualizarContaReceber(Long id, ContaReceber entrada) {
        Long anterior = contas.findAlunoIdById(id).orElseThrow(()->new RecursoNaoEncontradoException("Conta a receber não encontrada."));
        Long destino = alunoId(entrada); bloqueios.bloquearPessoasEmOrdem(List.of(anterior, destino));
        Aluno aluno = bloqueios.bloquearAluno(destino);
        if (!anterior.equals(destino)) bloqueios.exigirNaoArquivado(aluno);
        ContaReceber conta = contas.findLockedById(id).orElseThrow(()->new RecursoNaoEncontradoException("Conta a receber não encontrada."));
        em.refresh(conta, LockModeType.PESSIMISTIC_WRITE);
        if (!conta.getAluno().getId().equals(anterior)) throw new ConflitoException("O proprietário da cobrança mudou. Atualize e tente novamente.");
        boolean protegida = recebimentos.existsByContaReceberId(id) || conta.getStatus() == StatusFinanceiro.PAGO;
        boolean valorMudou = conta.getValor() == null?entrada.getValor() != null:entrada.getValor() == null || conta.getValor().compareTo(entrada.getValor()) != 0;
        if (protegida && (valorMudou || !anterior.equals(destino))) throw new ConflitoException("Valor e aluno não podem mudar após recebimento ou quitação histórica.");
        if (entrada.getStatus() != null && entrada.getStatus() != conta.getStatus()) throw new RegraNegocioException("O status é atualizado pelo registro de recebimentos.");
        boolean alterouIdentidade = !anterior.equals(destino) || entrada.getCompetencia() != null && !Objects.equals(entrada.getCompetencia(), conta.getCompetencia());
        if (!Objects.equals(conta.getDataVencimento(), entrada.getDataVencimento()))
            conta.setDiaVencimentoRecorrencia(entrada.getDataVencimento() == null?null:entrada.getDataVencimento().getDayOfMonth());
        conta.setAluno(aluno); conta.setDescricao(entrada.getDescricao()); conta.setValor(entrada.getValor()); conta.setDataVencimento(entrada.getDataVencimento());
        if (entrada.getCompetencia() != null) conta.setCompetencia(entrada.getCompetencia());
        preparar(conta);
        // Duplicidades legadas podem receber correções de descrição sem apagar histórico.
        if (alterouIdentidade) validarCompetenciaUnica(conta);
        return contas.saveAndFlush(conta);
    }
    private void preparar(ContaReceber c) {
        if (c.getDataVencimento() != null) {
            if (c.getDataVencimento().getYear()<1 || c.getDataVencimento().getYear()>9999) throw new RegraNegocioException("Informe vencimento entre os anos 0001 e 9999.");
            if (c.getCompetencia() == null) c.setCompetencia(YearMonth.from(c.getDataVencimento()).atDay(1));
            if (c.getDiaVencimentoRecorrencia() == null) c.setDiaVencimentoRecorrencia(c.getDataVencimento().getDayOfMonth());
        }
        if (c.getCompetencia() != null && (c.getCompetencia().getDayOfMonth() != 1 || c.getCompetencia().getYear()<1 || c.getCompetencia().getYear()>9999))
            throw new RegraNegocioException("Informe uma competência mensal válida.");
    }
    private void validarCompetenciaUnica(ContaReceber c) {
        if (c.getCompetencia() == null) return;
        String jpql = "select count(c) from ContaReceber c where c.aluno.id=:aluno and c.competencia=:mes";
        if (c.getId() != null) jpql+=" and c.id<>:id";
        var q = em.createQuery(jpql, Long.class).setParameter("aluno", c.getAluno().getId()).setParameter("mes", c.getCompetencia());
        if (c.getId() != null) q.setParameter("id", c.getId());
        if (q.getSingleResult()>0) throw new ConflitoException("Já existe cobrança deste aluno na competência informada.");
    }
    @Transactional
    public void gerarPrimeiraConta(Aluno referencia) {
        Aluno aluno = alunoParaNovaCobranca(referencia); salvarNovaConta(aluno, LocalDate.now(clock).plusDays(30), null, null);
    }
    @Transactional
    public void gerarPrimeiraContaSeAusente(Aluno referencia) {
        Aluno aluno = alunoParaNovaCobranca(referencia);
        if (contas.findTopByAlunoIdOrderByDataVencimentoDesc(aluno.getId()) == null) salvarNovaConta(aluno, LocalDate.now(clock).plusDays(30), null, null);
    }
    @Transactional
    public ContaReceber gerarProximaConta(Aluno referencia, LocalDate vencimento) {
        return salvarNovaConta(alunoParaNovaCobranca(referencia), vencimento, null, null);
    }
    @Transactional
    public void gerarRecorrenteSeNecessaria(Long id) {
        Aluno aluno = bloqueios.bloquearAluno(id);
        if (aluno.isArquivado() || aluno.getMensalidade() == null || aluno.getMensalidade().signum() <= 0) return;
        ContaReceber ultima = contas.ultimaContaRecorrente(id);
        if (ultima == null) {
            if (contas.findTopByAlunoIdOrderByDataVencimentoDesc(id) == null) salvarNovaConta(aluno, LocalDate.now(clock).plusDays(30), null, null);
            return;
        }
        if (ultima.getDataVencimento().isAfter(LocalDate.now(clock))) return;
        YearMonth proximo = YearMonth.from(ultima.getDataVencimento()).plusMonths(1);
        int dia = ultima.getDiaVencimentoRecorrencia() == null?ultima.getDataVencimento().getDayOfMonth():ultima.getDiaVencimentoRecorrencia();
        LocalDate competencia = (ultima.getCompetencia() == null?ultima.getDataVencimento().withDayOfMonth(1):ultima.getCompetencia()).plusMonths(1);
        Long existentes = em.createQuery("select count(c) from ContaReceber c where c.aluno.id=:id and c.competencia=:mes", Long.class)
                .setParameter("id", id).setParameter("mes", competencia).getSingleResult();
        if (existentes == 0) salvarNovaConta(aluno, proximo.atDay(Math.min(dia, proximo.lengthOfMonth())), competencia, dia);
    }
    private Aluno alunoParaNovaCobranca(Aluno referencia) {
        if (referencia == null || referencia.getId() == null) throw new RegraNegocioException("Aluno é obrigatório.");
        Aluno aluno = bloqueios.bloquearAluno(referencia.getId()); bloqueios.exigirNaoArquivado(aluno);
        if (aluno.getMensalidade() == null || aluno.getMensalidade().signum() <= 0) throw new RegraNegocioException("A mensalidade do aluno deve ser maior que zero");
        return aluno;
    }
    private ContaReceber salvarNovaConta(Aluno aluno, LocalDate vencimento, LocalDate competencia, Integer dia) {
        ContaReceber c = new ContaReceber(); c.setAluno(aluno); c.setDescricao("Matrícula - "+aluno.getNome()); c.setValor(aluno.getMensalidade());
        c.setDataVencimento(vencimento); c.setCompetencia(competencia); c.setDiaVencimentoRecorrencia(dia); c.setStatus(StatusFinanceiro.PENDENTE);
        preparar(c); validarCompetenciaUnica(c); return contas.saveAndFlush(c);
    }
    private Long alunoId(ContaReceber conta) {
        if (conta.getAluno() == null || conta.getAluno().getId() == null) throw new RegraNegocioException("Aluno é obrigatório.");
        return conta.getAluno().getId();
    }
}
