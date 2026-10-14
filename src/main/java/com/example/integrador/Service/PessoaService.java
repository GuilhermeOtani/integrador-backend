package com.example.integrador.Service;

import com.example.integrador.Model.Pessoa;
import com.example.integrador.Repository.PessoaRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@Service
public class PessoaService {
    private final PessoaRepository pessoaRepository;
    private final PessoaBloqueioService bloqueios;
    private final AlunoService alunoService;
    private final PessoaExclusaoTransacionalService exclusao;

    public PessoaService(PessoaRepository pessoaRepository,
                         PessoaBloqueioService bloqueios, AlunoService alunoService,
                         PessoaExclusaoTransacionalService exclusao) {
        this.pessoaRepository = pessoaRepository;
        this.bloqueios = bloqueios;
        this.alunoService = alunoService;
        this.exclusao = exclusao;
    }

    public List<Pessoa> listarTodosPessoa() {
        return pessoaRepository.findAll();
    }

    @Transactional
    public Pessoa salvarPessoa(Pessoa pessoa) {
        if (pessoa.getId() != null) return atualizarPessoa(pessoa.getId(), pessoa);
        if (pessoa instanceof com.example.integrador.Model.Aluno aluno) aluno.setArquivado(false);
        return pessoaRepository.save(pessoa);
    }

    public Pessoa buscarPessoaPorId(Long id) {
        return pessoaRepository
                .findById(id).orElseThrow(() -> new RuntimeException("Pessoa de ID " + id + " não encontrado"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deletarPessoaPorId(Long id) {
        Pessoa pessoa = pessoaRepository.findById(id)
                .orElseThrow(() -> new com.example.integrador.Exception.RecursoNaoEncontradoException("Pessoa não encontrada."));
        if (pessoa instanceof com.example.integrador.Model.Aluno) alunoService.deletarAlunoPorId(id);
        else exclusao.excluirNaoAluno(id);
    }

    @Transactional
    public Pessoa atualizarPessoa(Long id, Pessoa pessoa) {
        Pessoa pessoaSalvo = bloqueios.bloquearPessoa(id);
        BeanUtils.copyProperties(pessoa, pessoaSalvo, "id", "arquivado");
        return pessoaRepository.save(pessoaSalvo);
    }

    public Pessoa buscarPessoaPorNome(String nome) {
        return pessoaRepository.findByNome(nome);
    }
}
