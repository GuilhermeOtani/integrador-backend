package com.example.integrador.DTO;

import com.example.integrador.Enum.StatusMatricula;
import com.example.integrador.Enum.TipoPessoa;
import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.Motorista;
import com.example.integrador.Model.Pessoa;
import com.example.integrador.Model.Usuario;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MeResponse(
        Long id,
        String email,
        TipoPessoa tipoPessoa,
        PessoaPerfil pessoa
) {
    public static MeResponse fromEntity(Usuario usuario) {
        Pessoa pessoa = usuario.getPessoa();
        AlunoPerfil aluno = null;
        MotoristaPerfil motorista = null;

        if (pessoa instanceof Aluno entidade) {
            aluno = new AlunoPerfil(
                    entidade.getMatricula(),
                    entidade.getStatusMatricula(),
                    entidade.getDataCadastro(),
                    entidade.getMensalidade(),
                    entidade.getFaculdade() == null ? null : entidade.getFaculdade().getId());
        } else if (pessoa instanceof Motorista entidade) {
            motorista = new MotoristaPerfil(entidade.getCnh(), entidade.getSalario());
        }

        return new MeResponse(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getTipoPessoa(),
                new PessoaPerfil(
                        pessoa.getId(), pessoa.getNome(), pessoa.getCpfCnpj(), pessoa.getTelefone(),
                        aluno, motorista));
    }

    public record PessoaPerfil(
            Long id,
            String nome,
            String cpfCnpj,
            String telefone,
            AlunoPerfil aluno,
            MotoristaPerfil motorista
    ) {
    }

    public record AlunoPerfil(
            String matricula,
            StatusMatricula statusMatricula,
            LocalDate dataCadastro,
            BigDecimal mensalidade,
            Long faculdadeId
    ) {
    }

    public record MotoristaPerfil(String cnh, BigDecimal salario) {
    }
}
