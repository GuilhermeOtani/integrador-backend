package com.example.integrador.Whatsapp;

import com.example.integrador.Model.*;
import com.example.integrador.Repository.UsuarioRepository;
import com.example.integrador.Exception.RecursoNaoEncontradoException;
import com.example.integrador.Whatsapp.WhatsappDto.*;
import com.example.integrador.Whatsapp.WhatsappTipos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class WhatsappPreparacaoService {
    private final WhatsappRepos repos;
    private final UsuarioRepository usuarios;
    private final ProgramacaoWhatsappService programacoes;
    private final WhatsappDisponibilidadeService disponibilidade;
    public WhatsappPreparacaoService(WhatsappRepos repos, UsuarioRepository usuarios,
                                    ProgramacaoWhatsappService programacoes, WhatsappDisponibilidadeService disponibilidade) {
        this.repos = repos; this.usuarios = usuarios; this.programacoes = programacoes; this.disponibilidade = disponibilidade;
    }
    public record Preparado(Long usuarioId, String nome, String numero, long assinaturaRevisao,
                            Programacao programacao, String impedimento) {}

    @Transactional(readOnly = true)
    public List<Preparado> preparar(Publico publico) {
        return repos.destinatarios().stream().filter(a -> publico == Publico.TODOS
                || publico == Publico.ALUNOS && a.getUsuario().getPessoa() instanceof Aluno
                || publico == Publico.MOTORISTAS && a.getUsuario().getPessoa() instanceof Motorista)
                .filter(a -> a.getUsuario().getPessoa() instanceof Aluno || a.getUsuario().getPessoa() instanceof Motorista)
                .map(a -> {
                    var previa = previa(a.getUsuario().getId());
                    return new Preparado(a.getUsuario().getId(), ProgramacaoWhatsappService.valor(a.getUsuario().getPessoa().getNome(), "Destinatário"),
                        a.getNumero(), a.getRevisao(), previa.programacao(),
                        previa.impedimentos().isEmpty() ? null : String.join("; ", previa.impedimentos()));
                }).toList();
    }

    @Transactional(readOnly = true)
    public PreviaResponse previa(Long usuarioId) {
        var usuario = usuarios.findById(usuarioId).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
        var programacao = programacoes.consultar(usuario);
        var assinatura = repos.assinatura(usuarioId);
        var impedimentos = new ArrayList<String>();
        if (!usuario.isAtivo()) impedimentos.add("CONTA_INATIVA");
        if (assinatura == null || assinatura.getStatus() != StatusAssinatura.ATIVA) impedimentos.add("WHATSAPP_NAO_CONFIRMADO");
        if (assinatura != null && !disponibilidade.permitido(assinatura.getNumero())) impedimentos.add("NUMERO_FORA_DA_LISTA_DE_TESTE");
        if (usuario.getPessoa() instanceof Aluno aluno && aluno.getFaculdade() == null) impedimentos.add("SEM_FACULDADE");
        if (programacao.viagens().isEmpty()) impedimentos.add("SEM_PROGRAMACAO");
        return new PreviaResponse(usuarioId, usuario.getPessoa().getNome(), impedimentos.isEmpty(), List.copyOf(impedimentos), programacao);
    }
}
