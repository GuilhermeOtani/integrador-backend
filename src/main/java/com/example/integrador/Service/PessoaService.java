package com.example.integrador.Service;

import com.example.integrador.Model.Pessoa;
import com.example.integrador.Repository.PessoaRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PessoaService {
    private final PessoaRepository pessoaRepository;

    public PessoaService(PessoaRepository pessoaRepository) {
        this.pessoaRepository = pessoaRepository;
    }

    public List<Pessoa> listarTodosPessoa() {
        return pessoaRepository.findAll();
    }

    public Pessoa salvarPessoa(Pessoa pessoa) {
        return pessoaRepository.save(pessoa);
    }

    public Pessoa buscarPessoaPorId(Long id) {
        return pessoaRepository
                .findById(id).orElseThrow(() -> new RuntimeException("Pessoa de ID " + id + " não encontrado"));
    }

    public void deletarPessoaPorId(Long id) {
        pessoaRepository.deleteById(id);
    }

    public Pessoa atualizarPessoa(Long id, Pessoa pessoa) {
        Pessoa pessoaSalvo = buscarPessoaPorId(id);
        BeanUtils.copyProperties(pessoa, pessoaSalvo, "id");
        return pessoaRepository.save(pessoaSalvo);
    }

    public Pessoa buscarPessoaPorNome(String nome) {
        return pessoaRepository.findByNome(nome);
    }
}
