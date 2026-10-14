package com.example.integrador.Service;

import com.example.integrador.DTO.RecebimentoResponse;
import com.example.integrador.Enum.FormaPagamento;
import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Enum.StatusMatricula;
import com.example.integrador.Exception.ConflitoException;
import com.example.integrador.Exception.RecursoNaoEncontradoException;
import com.example.integrador.Model.ContaReceber;
import com.example.integrador.Model.Recebimento;
import com.example.integrador.Repository.AlunoRepository;
import com.example.integrador.Repository.ContaReceberRepository;
import com.example.integrador.Repository.RecebimentoRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
public class RecebimentoService {
    private final RecebimentoRepository recebimentoRepository;
    private final ContaReceberRepository contaReceberRepository;
    private final AlunoRepository alunoRepository;
    private final PessoaBloqueioService bloqueios;
    private final EntityManager em;

    public RecebimentoService(RecebimentoRepository recebimentoRepository,
                              ContaReceberRepository contaReceberRepository,
                              AlunoRepository alunoRepository, PessoaBloqueioService bloqueios, EntityManager em) {
        this.recebimentoRepository = recebimentoRepository;
        this.contaReceberRepository = contaReceberRepository;
        this.alunoRepository = alunoRepository;
        this.bloqueios = bloqueios;
        this.em = em;
    }

    @Transactional
    @PreAuthorize("hasRole('ALUNO')")
    public RecebimentoResponse realizarPagamento(Long contaId, FormaPagamento formaPagamento,
                                                  Long alunoAutenticadoId) {
        Long proprietario = contaReceberRepository.findAlunoIdById(contaId)
                .filter(id -> id.equals(alunoAutenticadoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Matrícula não encontrada."));
        var aluno = bloqueios.bloquearAluno(proprietario);
        bloqueios.exigirNaoArquivado(aluno);
        ContaReceber conta = contaReceberRepository.findLockedById(contaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Matrícula de ID " + contaId + " não encontrada"));
        em.refresh(conta, LockModeType.PESSIMISTIC_WRITE);
        validarProprietario(conta, alunoAutenticadoId);

        if (conta.getStatus() == StatusFinanceiro.PAGO
                || recebimentoRepository.existsByContaReceberId(contaId)) {
            throw new ConflitoException("Esta matrícula já foi paga");
        }

        Recebimento recebimento = new Recebimento();
        recebimento.setContaReceber(conta);
        recebimento.setDataRecebimento(LocalDate.now());
        recebimento.setFormaPagamento(formaPagamento);
        recebimento.setValor(conta.getValor());
        Recebimento salvo = recebimentoRepository.save(recebimento);

        conta.setStatus(StatusFinanceiro.PAGO);
        contaReceberRepository.save(conta);

        aluno.setStatusMatricula(StatusMatricula.ATIV0);
        alunoRepository.save(aluno);

        return RecebimentoResponse.fromEntity(salvo);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ALUNO')")
    public RecebimentoResponse buscarRecibo(Long contaId, Long alunoAutenticadoId) {
        Recebimento recebimento = recebimentoRepository.findByContaReceberId(contaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Recibo da matrícula de ID " + contaId + " não encontrado"));
        validarProprietario(recebimento.getContaReceber(), alunoAutenticadoId);
        return RecebimentoResponse.fromEntity(recebimento);
    }

    private void validarProprietario(ContaReceber conta, Long alunoAutenticadoId) {
        if (!conta.getAluno().getId().equals(alunoAutenticadoId)) {
            throw new RecursoNaoEncontradoException(
                    "Matrícula de ID " + conta.getId() + " não encontrada");
        }
    }
}
