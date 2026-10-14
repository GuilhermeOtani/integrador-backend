package com.example.integrador.Service;

import com.example.integrador.DTO.ContaReceberResponse;
import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Exception.*;
import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.ContaReceber;
import com.example.integrador.Repository.ContaReceberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ContaReceberService {
    private final ContaReceberRepository contareceberRepository;
    private final PessoaBloqueioService bloqueios;
    private final EntityManager em;
    public ContaReceberService(ContaReceberRepository contareceberRepository, PessoaBloqueioService bloqueios, EntityManager em) {
        this.contareceberRepository = contareceberRepository;
        this.bloqueios = bloqueios;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public List<ContaReceberResponse> listarTodasContaReceber() {
        return contareceberRepository.findAll().stream().map(ContaReceberResponse::fromEntity).toList();
    }

    @Transactional
    public ContaReceber salvarContaReceber(ContaReceber conta) {
        if (conta.getId() != null) return atualizarContaReceber(conta.getId(), conta);
        Aluno aluno = bloqueios.bloquearAluno(alunoId(conta));
        bloqueios.exigirNaoArquivado(aluno);
        conta.setAluno(aluno);
        return contareceberRepository.save(conta);
    }

    public ContaReceber buscarContaReceberPorId(Long id) {
        return contareceberRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta a receber não encontrada."));
    }

    public void deletarContaReceberPorId(Long id) { contareceberRepository.deleteById(id); }

    @Transactional
    public ContaReceber atualizarContaReceber(Long id, ContaReceber entrada) {
        Long anterior = contareceberRepository.findAlunoIdById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta a receber não encontrada."));
        Long destino = alunoId(entrada);
        bloqueios.bloquearPessoasEmOrdem(List.of(anterior, destino));
        Aluno aluno = bloqueios.bloquearAluno(destino);
        if (!anterior.equals(destino)) bloqueios.exigirNaoArquivado(aluno);
        ContaReceber conta = contareceberRepository.findLockedById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta a receber não encontrada."));
        em.refresh(conta, LockModeType.PESSIMISTIC_WRITE);
        if (!conta.getAluno().getId().equals(anterior))
            throw new ConflitoException("O proprietário da cobrança mudou. Atualize e tente novamente.");
        conta.setAluno(aluno);
        conta.setDescricao(entrada.getDescricao());
        conta.setValor(entrada.getValor());
        conta.setDataVencimento(entrada.getDataVencimento());
        conta.setStatus(entrada.getStatus());
        return conta;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ALUNO')")
    public List<ContaReceberResponse> listarMinhasContas(Long alunoId) {
        return contareceberRepository.findAllByAlunoIdOrderByDataVencimentoDesc(alunoId).stream()
                .map(ContaReceberResponse::fromEntity).toList();
    }

    @Transactional
    public void gerarPrimeiraConta(Aluno referencia) {
        Aluno aluno = alunoParaNovaCobranca(referencia);
        salvarNovaConta(aluno, LocalDate.now().plusDays(30));
    }

    @Transactional
    public void gerarPrimeiraContaSeAusente(Aluno referencia) {
        Aluno aluno = alunoParaNovaCobranca(referencia);
        if (contareceberRepository.findTopByAlunoIdOrderByDataVencimentoDesc(aluno.getId()) == null)
            salvarNovaConta(aluno, LocalDate.now().plusDays(30));
    }

    @Transactional
    public ContaReceber gerarProximaConta(Aluno referencia, LocalDate dataVencimento) {
        return salvarNovaConta(alunoParaNovaCobranca(referencia), dataVencimento);
    }

    /** Reconsulta estado e última cobrança depois do lock; a seleção inicial da rotina não é autoridade. */
    @Transactional
    public void gerarRecorrenteSeNecessaria(Long id) {
        Aluno aluno = bloqueios.bloquearAluno(id);
        if (aluno.isArquivado() || aluno.getMensalidade() == null || aluno.getMensalidade().signum() <= 0) return;
        ContaReceber ultima = contareceberRepository.findTopByAlunoIdOrderByDataVencimentoDesc(id);
        if (ultima == null) salvarNovaConta(aluno, LocalDate.now().plusDays(30));
        else if (ultima.getDataVencimento() != null && !ultima.getDataVencimento().isAfter(LocalDate.now()))
            salvarNovaConta(aluno, ultima.getDataVencimento().plusDays(30));
    }

    private Aluno alunoParaNovaCobranca(Aluno referencia) {
        if (referencia == null || referencia.getId() == null) throw new RegraNegocioException("Aluno é obrigatório.");
        Aluno aluno = bloqueios.bloquearAluno(referencia.getId());
        bloqueios.exigirNaoArquivado(aluno);
        if (aluno.getMensalidade() == null || aluno.getMensalidade().signum() <= 0)
            throw new RegraNegocioException("A mensalidade do aluno deve ser maior que zero");
        return aluno;
    }

    private ContaReceber salvarNovaConta(Aluno aluno, LocalDate vencimento) {
        ContaReceber conta = new ContaReceber();
        conta.setDescricao("Matrícula - " + aluno.getNome());
        conta.setValor(aluno.getMensalidade());
        conta.setDataVencimento(vencimento);
        conta.setStatus(StatusFinanceiro.PENDENTE);
        conta.setAluno(aluno);
        return contareceberRepository.save(conta);
    }
    private Long alunoId(ContaReceber conta) {
        if (conta.getAluno() == null || conta.getAluno().getId() == null)
            throw new RegraNegocioException("Aluno é obrigatório.");
        return conta.getAluno().getId();
    }
}
