package com.example.integrador.Repository;

import com.example.integrador.Model.ChamadoManutencao;
import com.example.integrador.Model.Motorista;
import com.example.integrador.Model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
}
