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
package org.apache.camel.component.consul.springboot.cloud;

import org.apache.camel.component.consul.cloud.ConsulServiceRegistry;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.support.IntrospectionSupport;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@AutoConfigureBefore(CamelAutoConfiguration.class)
@ConditionalOnProperty(prefix = "camel.component.consul.service-registry", name = "enabled")
@EnableConfigurationProperties(ConsulServiceRegistryConfiguration.class)
public class ConsulServiceRegistryAutoConfiguration {

    @Bean(name = "consul-service-registry")
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    public ConsulServiceRegistry consulServiceRegistry(ConsulServiceRegistryConfiguration configuration) throws Exception {
        ConsulServiceRegistry service = new ConsulServiceRegistry();

        IntrospectionSupport.setProperties(
            service,
            IntrospectionSupport.getNonNullProperties(configuration)
        );

        return service;
    }
}
