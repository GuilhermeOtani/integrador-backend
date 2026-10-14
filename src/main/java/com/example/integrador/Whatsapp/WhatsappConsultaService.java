package com.example.integrador.Whatsapp;

import com.example.integrador.Whatsapp.WhatsappDto.Programacao;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WhatsappConsultaService {
    private final WhatsappIdentidade identidade;
    private final ProgramacaoWhatsappService programacoes;
    public WhatsappConsultaService(WhatsappIdentidade identidade, ProgramacaoWhatsappService programacoes) {
        this.identidade = identidade; this.programacoes = programacoes;
    }
    @PreAuthorize("hasAnyRole('ALUNO','MOTORISTA')")
    @Transactional(readOnly = true)
    public Programacao minhaProgramacao() { return programacoes.consultar(identidade.atual()); }
}
