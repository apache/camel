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
package org.apache.camel.spring.boot.cluster;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.converter.TimePatternConverter;
import org.apache.camel.impl.cluster.ClusteredRouteConfiguration;
import org.apache.camel.impl.cluster.ClusteredRouteController;
import org.apache.camel.impl.cluster.ClusteredRouteFilter;
import org.apache.camel.impl.cluster.ClusteredRouteFilters;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.util.ObjectHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@AutoConfigureBefore(CamelAutoConfiguration.class)
@ConditionalOnProperty(prefix = "camel.clustered.controller", name = "enabled")
@EnableConfigurationProperties(ClusteredRouteControllerConfiguration.class)
public class ClusteredRouteControllerAutoConfiguration {

    @Autowired(required = false)
    private List<ClusteredRouteFilter> filters = Collections.emptyList();

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    @ConditionalOnMissingBean
    @ConditionalOnBean(CamelClusterService.class)
    public RouteController routeController(ClusteredRouteControllerConfiguration configuration) {
        ClusteredRouteController controller = new ClusteredRouteController();
        controller.setNamespace(configuration.getNamespace());

        Optional.ofNullable(configuration.getInitialDelay())
            .map(TimePatternConverter::toMilliSeconds)
            .map(Duration::ofMillis)
            .ifPresent(controller::setInitialDelay);

        controller.setFilters(filters);
        controller.addFilter(new ClusteredRouteFilters.IsAutoStartup());

        if (ObjectHelper.isNotEmpty(configuration.getClusterService())) {
            controller.setClusterService(configuration.getClusterService());
        }

        for (Map.Entry<String, ClusteredRouteControllerConfiguration.RouteConfiguration> entry: configuration.getRoutes().entrySet()) {
            final String routeId = entry.getKey();
            final ClusteredRouteControllerConfiguration.RouteConfiguration conf = entry.getValue();

            if (conf.isClustered()) {
                ClusteredRouteConfiguration routeConfiguration = new ClusteredRouteConfiguration();

                routeConfiguration.setNamespace(
                    Optional.ofNullable(conf.getNamespace())
                        .orElseGet(controller::getNamespace)
                );
                routeConfiguration.setInitialDelay(
                    Optional.ofNullable(conf.getInitialDelay())
                        .map(TimePatternConverter::toMilliSeconds)
                        .map(Duration::ofMillis)
                        .orElseGet(controller::getInitialDelay)
                );

                controller.addRouteConfiguration(routeId, routeConfiguration);
            } else {
                controller.addFilter(new ClusteredRouteFilters.BlackList(routeId));
            }
        }

        return controller;
    }
}
