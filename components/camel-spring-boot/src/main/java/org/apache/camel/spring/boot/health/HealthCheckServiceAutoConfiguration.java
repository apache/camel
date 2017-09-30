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
package org.apache.camel.spring.boot.health;


import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.camel.converter.TimePatternConverter;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckService;
import org.apache.camel.impl.health.DefaultHealthCheckService;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.spring.boot.util.GroupCondition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@AutoConfigureAfter(CamelAutoConfiguration.class)
@Conditional(HealthCheckServiceAutoConfiguration.Condition.class)
@EnableConfigurationProperties(HealthCheckServiceConfiguration.class)
public class HealthCheckServiceAutoConfiguration {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    @ConditionalOnBean(HealthCheckRegistry.class)
    @ConditionalOnMissingBean
    public HealthCheckService healthCheckService(HealthCheckServiceConfiguration configuration) {
        final DefaultHealthCheckService service = new DefaultHealthCheckService();

        Optional.ofNullable(configuration.getCheckInterval())
            .map(TimePatternConverter::toMilliSeconds)
            .ifPresent(interval -> service.setCheckInterval(interval, TimeUnit.MILLISECONDS));

        configuration.getChecks().entrySet().stream()
            .filter(entry -> Objects.nonNull(entry.getValue().getOptions()))
            .forEach(entry -> service.setHealthCheckOptions(entry.getKey(), entry.getValue().getOptions()));

        return service;
    }

    // ***************************************
    // Condition
    // ***************************************

    public static class Condition extends GroupCondition {
        public Condition() {
            super(
                HealthConstants.HEALTH_PREFIX,
                HealthConstants.HEALTH_CHECK_SERVICE_PREFIX
            );
        }
    }
}
