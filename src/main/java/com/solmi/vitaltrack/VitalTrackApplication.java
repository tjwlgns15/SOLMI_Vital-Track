package com.solmi.vitaltrack;

import com.solmi.vitaltrack.auth.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaAuditing
@EnableScheduling
@EnableConfigurationProperties(JwtProperties.class)
@SpringBootApplication
public class VitalTrackApplication {

	public static void main(String[] args) {
		SpringApplication.run(VitalTrackApplication.class, args);
	}
}
