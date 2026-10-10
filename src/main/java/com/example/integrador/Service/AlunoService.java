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

import java.time.LocalDate;
import java.util.List;

@Service
public class AlunoService {


    private final AlunoRepository alunoRepository;

    private final FaculdadeRepository faculdadeRepository;
    private final ContaReceberService contaReceberService;
    private final ProtecaoHistoricoManutencaoService protecaoHistorico;

    public AlunoService(AlunoRepository alunoRepository,
                        FaculdadeRepository faculdadeRepository,
                        ContaReceberService contaReceberService,
                        ProtecaoHistoricoManutencaoService protecaoHistorico) {
        this.alunoRepository = alunoRepository;
        this.faculdadeRepository = faculdadeRepository;
        this.contaReceberService = contaReceberService;
        this.protecaoHistorico = protecaoHistorico;
    }

    public List<Aluno> listarTodosAlunos() {
        return alunoRepository.findAll();
    }

    @Transactional
    public Aluno salvarAluno(AlunoDTO dto) {
        Aluno aluno = new Aluno();
        BeanUtils.copyProperties(dto, aluno, "id");
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

    public Aluno buscarAlunoPorId(Long id) {
        return alunoRepository
                .findById(id).orElseThrow(() -> new RuntimeException("Aluno de ID " + id + " não encontrado"));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deletarAlunoPorId(Long id) {
        protecaoHistorico.verificarPessoa(id);
        alunoRepository.deleteById(id);
    }

    @Transactional
    public Aluno atualizarAluno(Long id, AlunoDTO dto) {
        Aluno alunoSalvo = buscarAlunoPorId(id);
        BeanUtils.copyProperties(dto, alunoSalvo, "id");
        alunoSalvo.setTipoPessoa(TipoPessoa.ALUNO);
        if (dto.getFaculdadeId() != null) {
            alunoSalvo.setFaculdade(faculdadeRepository.getReferenceById(dto.getFaculdadeId()));
        } else {
            alunoSalvo.setFaculdade(null);
        }
        Aluno alunoAtualizado = alunoRepository.save(alunoSalvo);
        contaReceberService.gerarPrimeiraContaSeAusente(alunoAtualizado);
        return alunoAtualizado;
    }

    public Aluno buscarAlunoPorNome(String nome) {
        return alunoRepository.findByNome(nome);
    }

}
