package org.apache.camel.itest.springboot;

import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

/**
 * Loads 'META-INF/spring/spring.xml' if present.
 */
@Configuration
@ConditionalOnResource(resources = "META-INF/spring/spring.xml")
@ImportResource("META-INF/spring/spring.xml")
public class ITestXmlConfiguration {
}
