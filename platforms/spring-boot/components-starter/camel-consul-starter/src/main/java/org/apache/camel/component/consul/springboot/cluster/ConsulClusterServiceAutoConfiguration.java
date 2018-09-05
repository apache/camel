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
package org.apache.camel.component.consul.springboot.cluster;

import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.component.consul.cluster.ConsulClusterService;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.spring.boot.cluster.ClusteredRouteControllerAutoConfiguration;
import org.apache.camel.util.IntrospectionSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@AutoConfigureBefore({ ClusteredRouteControllerAutoConfiguration.class, CamelAutoConfiguration.class })
@ConditionalOnProperty(prefix = "camel.component.consul.cluster.service", name = "enabled")
@EnableConfigurationProperties(ConsulClusterServiceConfiguration.class)
public class ConsulClusterServiceAutoConfiguration {
    @Autowired
    private ConsulClusterServiceConfiguration configuration;

    @Bean(name = "consul-cluster-service")
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    public CamelClusterService consulClusterService() throws Exception {
        ConsulClusterService service = new ConsulClusterService();

        IntrospectionSupport.setProperties(
            service,
            IntrospectionSupport.getNonNullProperties(configuration)
        );

        return service;
    }
}
