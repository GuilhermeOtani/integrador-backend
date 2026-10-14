package com.example.integrador.Email;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface EmailLoteRepository extends JpaRepository<EmailLote, UUID> {}
