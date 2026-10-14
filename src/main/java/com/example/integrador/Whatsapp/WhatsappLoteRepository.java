package com.example.integrador.Whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WhatsappLoteRepository extends JpaRepository<WhatsappLote, UUID> {}
