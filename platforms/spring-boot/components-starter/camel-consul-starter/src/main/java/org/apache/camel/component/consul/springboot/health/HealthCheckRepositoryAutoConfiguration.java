/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.consul.springboot.health;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.component.consul.health.ConsulHealthCheckRepository;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

@Configuration
@AutoConfigureBefore(CamelAutoConfiguration.class)
@ConditionalOnProperty(prefix = "camel.component.consul.health.check.repository", value = "enabled")
@EnableConfigurationProperties(HealthCheckRepositoryConfiguration.class)
public class HealthCheckRepositoryAutoConfiguration {
    @Autowired
    private HealthCheckRepositoryConfiguration configuration;

    @Lazy
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    @ConditionalOnMissingBean(ConsulHealthCheckRepository.class)
    public HealthCheckRepository consulRepository() throws Exception {
        return new ConsulHealthCheckRepository.Builder()
            .configuration(configuration.asHealthCheckConfiguration())
            .checks(configuration.getChecks())
            .configurations(
                configuration.getConfigurations() != null
                    ? configuration.getConfigurations().entrySet()
                        .stream()
                        .collect(
                            Collectors.toMap(Map.Entry::getKey, e -> e.getValue().asHealthCheckConfiguration())
                        )
                    : Collections.emptyMap())
            .build();
    }
}