package org.airsonic.player.security;

import org.airsonic.player.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DLNAAuthenticationProvider implements AuthenticationProvider {

    @Autowired
    SettingsService settingsService;

    @Override
    public Authentication authenticate(Authentication auth) throws AuthenticationException {
        DLNAAuthenticationToken token = (DLNAAuthenticationToken) auth;
        if (settingsService.isDlnaEnabled() && token.getRequestedPath() != null && (
            token.getRequestedPath().startsWith("ext/coverArt.view") ||
            token.getRequestedPath().startsWith("ext/stream"))) {
            return new DLNAAuthenticationToken(token.getPrincipal(), "cred", token.getRequestedPath(), DLNA_AUTHORITIES, token);
        }
        return null;
    }

    public static List<GrantedAuthority> DLNA_AUTHORITIES = List.of(
            new SimpleGrantedAuthority("IS_AUTHENTICATED_FULLY"),
            new SimpleGrantedAuthority("ROLE_TEMP"));

    @Override
    public boolean supports(Class<?> authentication) {
        return DLNAAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
