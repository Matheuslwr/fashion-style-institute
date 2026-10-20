package br.com.fsistudio.store.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import br.com.fsistudio.store.account.CustomerAccountSessionFilter;

@Configuration
public class SecurityConfiguration {
    private final CustomerAccountSessionFilter customerSessionFilter;

    public SecurityConfiguration(CustomerAccountSessionFilter customerSessionFilter) {
        this.customerSessionFilter = customerSessionFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(Customizer.withDefaults())
                .referrerPolicy(policy -> policy.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**", "/uploads/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/account/register", "/api/account/verify-email",
                    "/api/account/verify-email/resend",
                    "/api/account/login", "/api/account/forgot-password", "/api/account/reset-password").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/account/**").hasRole("CUSTOMER")
                .anyRequest().permitAll())
            .httpBasic(Customizer.withDefaults())
            .addFilterBefore(customerSessionFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    FilterRegistrationBean<CustomerAccountSessionFilter> customerSessionFilterRegistration(
        CustomerAccountSessionFilter filter
    ) {
        FilterRegistrationBean<CustomerAccountSessionFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    UserDetailsService userDetailsService(
        @Value("${app.admin.username}") String username,
        @Value("${app.admin.password}") String password,
        PasswordEncoder passwordEncoder
    ) {
        return new InMemoryUserDetailsManager(User.withUsername(username)
            .password(passwordEncoder.encode(password))
            .roles("ADMIN")
            .build());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(origins.split(","))
            .map(String::trim).filter(origin -> !origin.isEmpty()).toList());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "Idempotency-Key"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}