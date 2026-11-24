package cl.duoc.moderation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class ModerationApplication {
    public static void main(String[] args) {
        SpringApplication.run(ModerationApplication.class, args);
    }

    @Bean
    RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    IdentityClient identityClient(RestTemplate http) {
        return new IdentityClient(http);
    }

    @Bean
    ForumClient forumClient(RestTemplate http) {
        return new ForumClient(http);
    }
}
