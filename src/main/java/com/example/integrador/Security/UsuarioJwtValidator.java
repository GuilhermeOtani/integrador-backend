package com.example.integrador.Security;

import com.example.integrador.Repository.UsuarioRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class UsuarioJwtValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "O usuário do token não existe, está inativo ou teve sua sessão revogada",
            null);

    private final UsuarioRepository usuarioRepository;

    public UsuarioJwtValidator(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Long usuarioId = lerUsuarioId(jwt);
        Long tokenVersion = lerTokenVersion(jwt);
        if (usuarioId == null || tokenVersion == null) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }

        boolean valido = usuarioRepository.tokenElegivel(
                usuarioId, tokenVersion);
        return valido
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }

    private Long lerUsuarioId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException exception) {
            return null;
        }
    }

    private Long lerTokenVersion(Jwt jwt) {
        Object claim = jwt.getClaim("ver");
        return claim instanceof Number number ? number.longValue() : null;
    }
}
