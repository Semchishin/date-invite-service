package semchishin.dateinviteservice.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import semchishin.dateinviteservice.web.RequestLoggingFilter;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String REALM = "Basic realm=\"date-invite\", charset=\"UTF-8\"";
    private static final int SECURITY_FILTER_ORDER = -100;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/admin", "/admin.html", "/admin.js", "/api/admin/**").authenticated()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, exception) -> {
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, REALM);
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"error\":\"Нужен логин и пароль администратора\"}");
                }))
                .build();
    }

    @Bean
    InMemoryUserDetailsManager adminUserDetails(AppProperties properties) {
        AppProperties.Admin admin = properties.admin();
        UserDetails user = User.withUsername(admin.usernameOrDefault())
                .password(admin.encodedPasswordOrDefault())
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registration =
                new FilterRegistrationBean<>(new RequestLoggingFilter());
        registration.setOrder(SECURITY_FILTER_ORDER - 1);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
