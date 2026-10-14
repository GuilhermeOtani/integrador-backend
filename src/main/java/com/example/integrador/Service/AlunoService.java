package com.example.integrador.Service;


import com.example.integrador.Model.Aluno;
import com.example.integrador.Enum.StatusMatricula;
import com.example.integrador.Enum.TipoPessoa;
import com.example.integrador.Repository.AlunoRepository;
import com.example.integrador.Repository.FaculdadeRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.integrador.DTO.AlunoDTO;
import com.example.integrador.Enum.SituacaoArquivamentoAluno;
import com.example.integrador.Exception.RecursoNaoEncontradoException;
import com.example.integrador.Exception.ExclusaoAlunoBloqueadaException;
import com.example.integrador.DTO.SituacaoExclusaoAlunoResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;

import java.time.LocalDate;
import java.util.List;

@Service
public class AlunoService {


    private final AlunoRepository alunoRepository;

    private final FaculdadeRepository faculdadeRepository;
    private final ContaReceberService contaReceberService;
    private final AlunoArquivamentoTransacionalService arquivamentoTransacional;
    private final PessoaBloqueioService bloqueios;

    public AlunoService(AlunoRepository alunoRepository,
                        FaculdadeRepository faculdadeRepository,
                        ContaReceberService contaReceberService,
                        AlunoArquivamentoTransacionalService arquivamentoTransacional,
                        PessoaBloqueioService bloqueios) {
        this.alunoRepository = alunoRepository;
        this.faculdadeRepository = faculdadeRepository;
        this.contaReceberService = contaReceberService;
        this.arquivamentoTransacional = arquivamentoTransacional;
        this.bloqueios = bloqueios;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<Aluno> listarTodosAlunos() {
        return listarTodosAlunos(SituacaoArquivamentoAluno.NAO_ARQUIVADOS);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<Aluno> listarTodosAlunos(SituacaoArquivamentoAluno situacao) {
        return switch (situacao) {
            case TODOS -> alunoRepository.findAll();
            case ARQUIVADOS -> alunoRepository.findAllByArquivado(true);
            case NAO_ARQUIVADOS -> alunoRepository.findAllByArquivado(false);
        };
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Aluno salvarAluno(AlunoDTO dto) {
        Aluno aluno = new Aluno();
        BeanUtils.copyProperties(dto, aluno, "id", "arquivado");
        aluno.setTipoPessoa(TipoPessoa.ALUNO);
        aluno.setDataCadastro(LocalDate.now());
        aluno.setStatusMatricula(StatusMatricula.PENDENTE);
        if (dto.getFaculdadeId() != null) {
            aluno.setFaculdade(faculdadeRepository.getReferenceById(dto.getFaculdadeId()));
        } else {
            aluno.setFaculdade(null);
        }
        Aluno alunoSalvo = alunoRepository.save(aluno);
        contaReceberService.gerarPrimeiraConta(alunoSalvo);
        return alunoSalvo;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Aluno buscarAlunoPorId(Long id) {
        return alunoRepository
                .findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Aluno não encontrado."));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deletarAlunoPorId(Long id) {
        try {
            arquivamentoTransacional.excluir(id);
        } catch (DataIntegrityViolationException | ConstraintViolationException exception) {
            // A transação pertence ao outro bean. Não consultar nem continuar após uma falha.
            throw new ExclusaoAlunoBloqueadaException("ALUNO_POSSUI_VINCULOS",
                    "Uma relação vinculada impede a exclusão. Mantenha o aluno arquivado para preservar o histórico.",
                    null);
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Aluno arquivarAluno(Long id, boolean arquivado) {
        return arquivamentoTransacional.arquivar(id, arquivado);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SituacaoExclusaoAlunoResponse consultarSituacaoExclusao(Long id) {
        return arquivamentoTransacional.situacao(id);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Aluno atualizarAluno(Long id, AlunoDTO dto) {
        Aluno alunoSalvo = bloqueios.bloquearAluno(id);
        BeanUtils.copyProperties(dto, alunoSalvo, "id", "arquivado");
        alunoSalvo.setTipoPessoa(TipoPessoa.ALUNO);
        if (dto.getFaculdadeId() != null) {
            alunoSalvo.setFaculdade(faculdadeRepository.getReferenceById(dto.getFaculdadeId()));
        } else {
            alunoSalvo.setFaculdade(null);
        }
        Aluno alunoAtualizado = alunoRepository.save(alunoSalvo);
        if (!alunoAtualizado.isArquivado()) contaReceberService.gerarPrimeiraContaSeAusente(alunoAtualizado);
        return alunoAtualizado;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Aluno buscarAlunoPorNome(String nome) {
        return alunoRepository.findByNome(nome);
    }

}
