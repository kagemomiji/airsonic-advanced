package org.airsonic.player.security;

import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.sonos.SonosLinkSecurityInterceptor.SonosJWTVerification;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collections;

@Configuration
@EnableWebSecurity
public class GlobalSecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalSecurityConfig.class);

    static final String FAILURE_URL = "/login?error";

    @Autowired
    private CsrfSecurityRequestMatcher csrfSecurityRequestMatcher;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private MultipleCredsMatchingAuthenticationProvider multipleCredsProvider;

    @Autowired
    private SonosJWTVerification sonosJwtVerification;

    @Autowired
    private DLNAAuthenticationProvider dlnaAuthProvider;

    @EventListener
    public void loginFailureListener(AbstractAuthenticationFailureEvent event) {
        if (event.getSource() instanceof AbstractAuthenticationToken) {
            AbstractAuthenticationToken token = (AbstractAuthenticationToken) event.getSource();
            Object details = token.getDetails();
            if (details instanceof WebAuthenticationDetails) {
                LOG.info("Login failed from [{}]", ((WebAuthenticationDetails) details).getRemoteAddress());
            }
        }
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        if (settingsService.isLdapEnabled()) {
            auth.ldapAuthentication()
                    .contextSource()
                        .managerDn(settingsService.getLdapManagerDn())
                        .managerPassword(settingsService.getLdapManagerPassword())
                        .url(settingsService.getLdapUrl())
                    .and()
                    .userSearchFilter(settingsService.getLdapSearchFilter())
                    .userDetailsContextMapper(new CustomUserDetailsContextMapper())
                    .ldapAuthoritiesPopulator(new CustomLDAPAuthenticatorPostProcessor.CustomLDAPAuthoritiesPopulator())
                    .addObjectPostProcessor(new CustomLDAPAuthenticatorPostProcessor(securityService, settingsService));
        }
        String jwtKey = settingsService.getJWTKey();
        if (StringUtils.isBlank(jwtKey)) {
            LOG.warn("Generating new jwt key");
            jwtKey = JWTSecurityService.generateKey();
            settingsService.setJWTKey(jwtKey);
            settingsService.save();
        }
        JWTAuthenticationProvider jwtAuth = new JWTAuthenticationProvider(jwtKey);
        jwtAuth.addAdditionalCheck("/ws/Sonos", sonosJwtVerification);
        auth.authenticationProvider(jwtAuth);
        auth.authenticationProvider(multipleCredsProvider);
        auth.authenticationProvider(dlnaAuthProvider);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    @Order(1)
    public DefaultSecurityFilterChain extSecurityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {

        http = http.addFilter(new WebAsyncManagerIntegrationFilter());
        http = http.addFilterBefore(
            new JWTRequestParameterProcessingFilter(
                authenticationManager,
                FAILURE_URL)
            , UsernamePasswordAuthenticationFilter.class);
        http = http.addFilterBefore(new DLNARequestParameterProcessingFilter(authenticationManager, FAILURE_URL), UsernamePasswordAuthenticationFilter.class);

        http
                .securityMatcher("/ext/**")
                .csrf((csrf) -> csrf
                        .requireCsrfProtectionMatcher(csrfSecurityRequestMatcher))
                .headers(header -> header.frameOptions(fp -> fp.sameOrigin()))
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers("/ext/stream/**", "/ext/coverArt*", "/ext/share/**", "/ext/hls/**",
                                "/ext/captions**")
                        .hasAnyRole("TEMP", "USER")
                        .anyRequest().authenticated())
                .sessionManagement(manager -> manager.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                        .sessionFixation().none())
                .exceptionHandling(Customizer.withDefaults())
                .securityContext(Customizer.withDefaults())
                .requestCache(Customizer.withDefaults())
                .anonymous(Customizer.withDefaults())
                .servletApi(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    @Order(2)
    public DefaultSecurityFilterChain webSecurityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {

        RESTRequestParameterProcessingFilter restAuthenticationFilter = new RESTRequestParameterProcessingFilter();
        restAuthenticationFilter.setAuthenticationManager(authenticationManager);

        // Try to load the 'remember me' key.
        //
        // Note that using a fixed key compromises security as perfect
        // forward secrecy is not guaranteed anymore.
        //
        // An external entity can then re-use our authentication cookies before
        // the expiration time, or even, given enough time, recover the password
        // from the MD5 hash.
        //
        // A null key means an ephemeral key is autogenerated
        String rememberMeKey = settingsService.getRememberMeKey();
        if (rememberMeKey != null) {
            LOG.info("Using a fixed 'remember me' key from properties, this is insecure.");
        }

        http
            .cors((cors) -> cors.configurationSource(corsConfigurationSource()))
            //.addFilterBefore(restAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .httpBasic(Customizer.withDefaults())
            .addFilterAfter(restAuthenticationFilter, BasicAuthenticationFilter.class)
            .csrf(csrf -> csrf.ignoringRequestMatchers("/ws/Sonos/**").requireCsrfProtectionMatcher(csrfSecurityRequestMatcher))
            .headers(header -> header.frameOptions(fo -> fo.sameOrigin()))
            .authorizeHttpRequests((authorize) -> authorize.requestMatchers("/recover*", "/accessDenied*", "/style/**", "/icons/**", "/flash/**", "/script/**",
                    "/login", "/error", "/sonos/**", "/sonoslink/**", "/ws/Sonos/**").permitAll().requestMatchers("/personalSettings*",
                    "/playerSettings*", "/shareSettings*", "/credentialsSettings*").hasRole("SETTINGS")
                    .requestMatchers("/generalSettings*", "/advancedSettings*", "/userSettings*", "/musicFolderSettings*",
                            "/databaseSettings*", "/transcodeSettings*", "/rest/startScan*").hasRole("ADMIN")
                    .requestMatchers("/deletePlaylist*", "/savePlaylist*").hasRole("PLAYLIST").requestMatchers("/download*").hasRole("DOWNLOAD")
                    .requestMatchers("/upload*").hasRole("UPLOAD").requestMatchers("/createShare*").hasRole("SHARE")
                    .requestMatchers("/changeCoverArt*", "/editTags*", "/editMediaDir*").hasRole("COVERART").requestMatchers("/setMusicFileInfo*").hasRole("COMMENT")
                    .requestMatchers("/podcastReceiverAdmin*", "/podcastEpisodes*").hasRole("PODCAST")
                    .requestMatchers("/**").hasRole("USER").anyRequest().authenticated())
            .formLogin((login) -> login
                    .loginPage("/login")
                    .permitAll()
                    .defaultSuccessUrl("/index", true)
                    .failureUrl(FAILURE_URL)
                    .usernameParameter("j_username")
                    .passwordParameter("j_password"))
            .logout((logout) -> logout
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                .clearAuthentication(true)
                .invalidateHttpSession(true)
                .logoutSuccessUrl("/login?logout"))
            .rememberMe((rememberMe) -> rememberMe
                .key(rememberMeKey)
                .userDetailsService(securityService));
        return http.build();
    }

    /*
    @Bean(name = "mvcHandlerMappingIntrospector")
    public HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
        return new HandlerMappingIntrospector();
    }
    */

    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Collections.singletonList("*"));
        configuration.setAllowedMethods(Collections.singletonList("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/rest/**", configuration);
        source.registerCorsConfiguration("/stream/**", configuration);
        source.registerCorsConfiguration("/hls**", configuration);
        source.registerCorsConfiguration("/captions**", configuration);
        source.registerCorsConfiguration("/ext/stream/**", configuration);
        source.registerCorsConfiguration("/ext/hls**", configuration);
        source.registerCorsConfiguration("/ext/captions**", configuration);
        return source;
    }
}
