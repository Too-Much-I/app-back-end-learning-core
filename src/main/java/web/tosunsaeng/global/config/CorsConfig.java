package web.tosunsaeng.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 모든 API 엔드포인트 허용
                // 🌟 로컬 테스트 주소와 실제 운영 도메인 주소(www 포함)까지 한 번에 안전하게 허용합니다.
                .allowedOrigins(
                        "http://localhost:3000",
                        "http://localhost:5173",
                        "https://tosunsaeng-blue.vercel.app/",
                        "https://to-teacher.com",
                        "https://www.to-teacher.com"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}