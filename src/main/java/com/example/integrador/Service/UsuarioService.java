package com.example.integrador.Service;

import com.example.integrador.Model.Usuario;
import com.example.integrador.Repository.UsuarioRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public List<Usuario> listarTodosUsuario() {
        return usuarioRepository.findAll();
    }

    public Usuario salvarUsuario(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }

    public Usuario buscarUsuarioPorId(Long id) {
        return usuarioRepository
                .findById(id).orElseThrow(() -> new RuntimeException("Usuario de ID " + id + " não encontrado"));
    }

    public void deletarUsuarioPorId(Long id) {
        usuarioRepository.deleteById(id);
    }

    public Usuario atualizarUsuario(Long id, Usuario usuario) {
        Usuario usuarioSalvo = buscarUsuarioPorId(id);
        BeanUtils.copyProperties(usuario, usuarioSalvo, "id");
        return usuarioRepository.save(usuarioSalvo);
    }
}
