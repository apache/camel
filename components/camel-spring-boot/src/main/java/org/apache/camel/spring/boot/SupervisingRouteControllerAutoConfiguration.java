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
package org.apache.camel.spring.boot;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.converter.TimePatternConverter;
import org.apache.camel.impl.SupervisingRouteController;
import org.apache.camel.impl.SupervisingRouteControllerFilters;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spring.boot.SupervisingRouteControllerConfiguration.BackOffConfiguration;
import org.apache.camel.spring.boot.SupervisingRouteControllerConfiguration.RouteConfiguration;
import org.apache.camel.util.backoff.BackOff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@AutoConfigureBefore(CamelAutoConfiguration.class)
@ConditionalOnProperty(prefix = "camel.supervising.controller", name = "enabled")
@EnableConfigurationProperties(SupervisingRouteControllerConfiguration.class)
public class SupervisingRouteControllerAutoConfiguration {
    @Autowired
    private SupervisingRouteControllerConfiguration configuration;
    @Autowired(required = false)
    private List<SupervisingRouteController.Filter> filters = Collections.emptyList();

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    @ConditionalOnMissingBean
    public RouteController routeController() {
        SupervisingRouteController controller = new SupervisingRouteController();

        // Initial delay
        Optional.ofNullable(configuration.getInitialDelay()).map(TimePatternConverter::toMilliSeconds).ifPresent(controller::setInitialDelay);

        // Filter list
        controller.setFilters(filters);

        // Back off
        controller.setDefaultBackOff(configureBackOff(Optional.empty(), configuration.getDefaultBackOff()));

        for (Map.Entry<String, RouteConfiguration> entry: configuration.getRoutes().entrySet()) {
            final RouteConfiguration cfg = entry.getValue();
            final Optional<BackOff> defaultBackOff = Optional.ofNullable(controller.getDefaultBackOff());

            if (!cfg.isSupervised()) {
                // Mark this route as excluded from supervisor
                controller.addFilter(new SupervisingRouteControllerFilters.BlackList(entry.getKey()));
            } else {
                // configure teh route
                controller.setBackOff(entry.getKey(), configureBackOff(defaultBackOff, cfg.getBackOff()));
            }
        }

        return controller;
    }

    private BackOff configureBackOff(Optional<BackOff> template, BackOffConfiguration conf) {
        final BackOff.Builder builder = template.map(BackOff::builder).orElseGet(BackOff::builder);

        Optional.ofNullable(conf.getDelay()).map(TimePatternConverter::toMilliSeconds).ifPresent(builder::delay);
        Optional.ofNullable(conf.getMaxDelay()).map(TimePatternConverter::toMilliSeconds).ifPresent(builder::maxDelay);
        Optional.ofNullable(conf.getMaxElapsedTime()).map(TimePatternConverter::toMilliSeconds).ifPresent(builder::maxElapsedTime);
        Optional.ofNullable(conf.getMaxAttempts()).ifPresent(builder::maxAttempts);
        Optional.ofNullable(conf.getMultiplier()).ifPresent(builder::multiplier);

        return builder.build();
    }
}
