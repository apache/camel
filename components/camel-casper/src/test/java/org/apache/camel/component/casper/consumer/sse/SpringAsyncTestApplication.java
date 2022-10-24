package org.apache.camel.component.casper.consumer.sse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring boot application to load a localhost casper sse server on port 8080 (tests)
 * @author p35862
 *
 */
@SpringBootApplication
@EnableAsync
public class SpringAsyncTestApplication implements WebMvcConfigurer {

	static ConfigurableApplicationContext  ctx;
	
	public static void main(String[] args) {
	   ctx = SpringApplication.run(SpringAsyncTestApplication.class, args);
	}

	
	@Override
	public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
		configurer.setTaskExecutor(mvcTaskExecutor());
	}

	@Bean
	public ThreadPoolTaskExecutor mvcTaskExecutor() {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setThreadNamePrefix("mvc-task-");
		return taskExecutor;
	}
	
	
	public static void shutdown() {
		 ctx.close();
	}

	
}
