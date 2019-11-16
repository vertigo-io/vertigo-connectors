package io.vertigo.connectors.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VertigoSpringBridgeConfiguration {

	@Bean
	public VertigoConfigBean vertigoConfigBean() {
		return new VertigoConfigBean();
	}

}
