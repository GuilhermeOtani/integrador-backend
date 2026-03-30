package com.example.integrador.DTO;

public class AlunoDTO {

    private Long id;
    private String nome;
    private String cpfCnpj;
    private String telefone;
    private String matricula;

    private Long faculdadeId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCpfCnpj() {
        return cpfCnpj;
    }

    public void setCpfCnpj(String cpfCnpj) {
        this.cpfCnpj = cpfCnpj;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getMatricula() {
        return matricula;
    }

    public void setMatricula(String matricula) {
        this.matricula = matricula;
    }

    public Long getFaculdadeId() {
        return faculdadeId;
    }

    public void setFaculdadeId(Long faculdadeId) {
        this.faculdadeId = faculdadeId;
    }
}
