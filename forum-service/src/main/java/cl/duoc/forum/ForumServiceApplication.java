package cl.duoc.forum;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class ForumServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ForumServiceApplication.class, args);
    }
}

@Configuration
class ForumSecurityConfiguration {
    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, IdentityClient client) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) ->
                        response.sendError(401)).accessDeniedHandler((request, response, exception) ->
                                response.sendError(403)))
                .addFilterBefore(new BearerFilter(client), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
