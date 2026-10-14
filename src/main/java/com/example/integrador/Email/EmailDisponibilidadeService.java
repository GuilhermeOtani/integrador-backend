package com.example.integrador.Email;

import com.example.integrador.Exception.IntegracaoEmailIndisponivelException;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class EmailDisponibilidadeService {
    private final EmailProperties properties;
    public EmailDisponibilidadeService(EmailProperties properties) { this.properties = properties; }

    public List<String> impedimentos() {
        List<String> erros = new ArrayList<>();
        if (!properties.isEnabled()) erros.add("Integração desativada no servidor.");
        if (properties.getSmtpHost().isBlank() || properties.getSmtpPort() < 1 || properties.getSmtpPort() > 65535)
            erros.add("Servidor SMTP não configurado.");
        if (!emailValido(properties.getFrom())) erros.add("Remetente não configurado ou inválido.");
        if (properties.getSmtpUsername().isBlank() != properties.getSmtpPassword().isBlank())
            erros.add("Informe usuário e senha SMTP juntos, ou deixe ambos vazios para um relay sem autenticação.");
        if (properties.isSsl() == properties.isStarttls())
            erros.add("Configure exatamente uma opção de segurança: STARTTLS ou SSL.");
        if (!properties.isProduction() && properties.getAllowedRecipients().isEmpty())
            erros.add("Modo de teste sem destinatários permitidos.");
        return List.copyOf(erros);
    }
    public void exigirDisponivel() {
        if (!impedimentos().isEmpty()) throw new IntegracaoEmailIndisponivelException(
                "Configure e habilite o SMTP no servidor antes de enviar ou ativar o automático.");
    }
    public boolean permitido(String email) {
        return emailValido(email) && (properties.isProduction() || properties.getAllowedRecipients().stream()
                .anyMatch(permitido -> normalizar(permitido).equals(normalizar(email))));
    }
    public static String normalizar(String email) { return email == null ? "" : email.strip().toLowerCase(Locale.ROOT); }
    public static boolean emailValido(String email) {
        if (email == null || email.length() > 255 || email.contains("\r") || email.contains("\n")) return false;
        try {
            InternetAddress address = new InternetAddress(email, true);
            address.validate();
            return address.getPersonal() == null && email.equals(address.getAddress())
                    && email.indexOf('@') > 0 && !email.contains(" ");
        } catch (jakarta.mail.internet.AddressException exception) { return false; }
    }
}
