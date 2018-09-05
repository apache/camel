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
package org.apache.camel.component.atomix.cluster.springboot;


import java.util.stream.Collectors;

import io.atomix.catalyst.transport.Address;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.component.atomix.cluster.AtomixClusterClientService;
import org.apache.camel.component.atomix.cluster.AtomixClusterService;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.spring.boot.cluster.ClusteredRouteControllerAutoConfiguration;
import org.apache.camel.util.ObjectHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@AutoConfigureBefore({ ClusteredRouteControllerAutoConfiguration.class, CamelAutoConfiguration.class })
@Conditional(AtomixClusterServiceAutoConfiguration.AutoConfigurationCondition.class)
@EnableConfigurationProperties(AtomixClusterServiceConfiguration.class)
public class AtomixClusterServiceAutoConfiguration {
    @Autowired
    private AtomixClusterServiceConfiguration configuration;

    @Bean(name = "atomix-cluster-service")
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    @ConditionalOnProperty(prefix = "camel.component.atomix.cluster.service", name = "mode", havingValue = "node")
    public CamelClusterService atomixClusterService() {
        AtomixClusterService service = new AtomixClusterService();
        service.setNodes(configuration.getNodes().stream().map(Address::new).collect(Collectors.toList()));

        ObjectHelper.ifNotEmpty(configuration.isEphemeral(), service::setEphemeral);
        ObjectHelper.ifNotEmpty(configuration.getId(), service::setId);
        ObjectHelper.ifNotEmpty(configuration.getAddress(), service::setAddress);
        ObjectHelper.ifNotEmpty(configuration.getStoragePath(), service::setStoragePath);
        ObjectHelper.ifNotEmpty(configuration.getStorageLevel(), service::setStorageLevel);
        ObjectHelper.ifNotEmpty(configuration.getConfigurationUri(), service::setConfigurationUri);
        ObjectHelper.ifNotEmpty(configuration.getAttributes(), service::setAttributes);
        ObjectHelper.ifNotEmpty(configuration.getOrder(), service::setOrder);

        return service;
    }

    @Bean(name = "atomix-cluster-client-service")
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    @ConditionalOnProperty(prefix = "camel.component.atomix.cluster.service", name = "mode", havingValue = "client")
    public CamelClusterService atomixClusterClientService() {
        AtomixClusterClientService service = new AtomixClusterClientService();
        service.setNodes(configuration.getNodes().stream().map(Address::new).collect(Collectors.toList()));

        ObjectHelper.ifNotEmpty(configuration.getId(), service::setId);
        ObjectHelper.ifNotEmpty(configuration.getConfigurationUri(), service::setConfigurationUri);
        ObjectHelper.ifNotEmpty(configuration.getAttributes(), service::setAttributes);
        ObjectHelper.ifNotEmpty(configuration.getOrder(), service::setOrder);

        return service;
    }

    // *****************************************
    // Conditions
    // *****************************************

    public static class AutoConfigurationCondition extends AllNestedConditions {
        public AutoConfigurationCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "camel.component.atomix.cluster.service", name = "enabled")
        static class IfEnabled {
        }

        @ConditionalOnProperty(prefix = "camel.component.atomix.cluster.service", name = "mode")
        static class WithMode {
        }
    }
}
