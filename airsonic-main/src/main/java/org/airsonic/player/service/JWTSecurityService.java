/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

  Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator.Builder;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.airsonic.player.security.JWTAuthenticationToken;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

@Service("jwtSecurityService")
public class JWTSecurityService {

    public static final String JWT_PARAM_NAME = "jwt";
    public static final String CLAIM_PATH = "path";

    // TODO make this configurable
    public static final int DEFAULT_DAYS_VALID_FOR = 7;
    private static SecureRandom secureRandom = new SecureRandom();

    private final SettingsService settingsService;

    @Autowired
    public JWTSecurityService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public static String generateKey() {
        BigInteger randomInt = new BigInteger(130, secureRandom);
        return randomInt.toString(32);
    }

    public static Algorithm getAlgorithm(String jwtKey) {
        return Algorithm.HMAC256(jwtKey);
    }

    private static String createToken(String jwtKey, String user, String path, Instant expireDate, Map<String, String> additionalClaims) {
        UriComponents components = UriComponentsBuilder.fromUriString(path).build();
        String query = components.getQuery();
        String pathClaim = components.getPath() + (!StringUtils.isBlank(query) ? "?" + components.getQuery() : "");
        Builder builder = JWT
                .create()
                .withIssuer("airsonic")
                .withSubject(user)
                .withClaim(CLAIM_PATH, pathClaim)
                .withExpiresAt(Date.from(expireDate));

        for (Entry<String, String> claim : additionalClaims.entrySet()) {
            builder = builder.withClaim(claim.getKey(), claim.getValue());
        }

        return builder.sign(getAlgorithm(jwtKey));
    }

    public String addJWTToken(String user, String uri) {
        return addJWTToken(user, UriComponentsBuilder.fromUriString(uri)).build().toString();
    }

    public UriComponentsBuilder addJWTToken(String user, UriComponentsBuilder builder) {
        return addJWTToken(user, builder, Collections.emptyMap());
    }

    public UriComponentsBuilder addJWTToken(String user, UriComponentsBuilder builder, Map<String, String> additionalClaims) {
        return addJWTToken(user, builder, null, additionalClaims);
    }

    public UriComponentsBuilder addJWTToken(String user, UriComponentsBuilder builder, Instant expires) {
        return addJWTToken(user, builder, expires, Collections.emptyMap());
    }

    public UriComponentsBuilder addJWTToken(String user, UriComponentsBuilder builder, Instant expires, Map<String, String> additionalClaims) {
        if (expires == null) {
            expires = Instant.now().plus(DEFAULT_DAYS_VALID_FOR, ChronoUnit.DAYS);
        }
        String token = JWTSecurityService.createToken(
                settingsService.getJWTKey(),
                user,
                builder.toUriString(),
                expires,
                additionalClaims);
        return builder.queryParam(JWTSecurityService.JWT_PARAM_NAME, token);
    }

    public static DecodedJWT verify(String jwtKey, String token) {
        Algorithm algorithm = JWTSecurityService.getAlgorithm(jwtKey);
        JWTVerifier verifier = JWT.require(algorithm).build();
        return verifier.verify(token);
    }

    public DecodedJWT verify(String credentials) {
        return verify(settingsService.getJWTKey(), credentials);
    }

    public static DecodedJWT decode(String token) {
        return JWT.decode(token);
    }

    public static Instant getExpiration(JWTAuthenticationToken auth) {
        return Optional.ofNullable(auth)
                .map(x -> (DecodedJWT) x.getDetails())
                .map(x -> x.getExpiresAt())
                .map(x -> x.toInstant())
                .orElse(null);
    }
}
