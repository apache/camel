package org.apache.camel.zipkin.starter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * A configuration controller to enable Zipkin via the configuration property.
 * Useful to bootstrap Zipkin when not using the {@link CamelZipkin} annotation.
 */
@Configuration
@ConditionalOnProperty(value = "camel.zipkin.enabled")
@Import(ZipkinAutoConfiguration.class)
public class ZipkinConditionalAutoConfiguration {
}
