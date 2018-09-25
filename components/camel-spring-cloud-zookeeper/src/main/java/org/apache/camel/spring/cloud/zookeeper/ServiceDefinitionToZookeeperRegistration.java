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
package org.apache.camel.spring.cloud.zookeeper;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.spring.boot.cloud.CamelCloudConfigurationProperties;
import org.springframework.cloud.zookeeper.discovery.ZookeeperDiscoveryProperties;
import org.springframework.cloud.zookeeper.discovery.ZookeeperInstance;
import org.springframework.cloud.zookeeper.serviceregistry.ServiceInstanceRegistration;
import org.springframework.cloud.zookeeper.serviceregistry.ZookeeperRegistration;
import org.springframework.core.convert.converter.Converter;

public final class ServiceDefinitionToZookeeperRegistration implements Converter<ServiceDefinition, ZookeeperRegistration> {
    private final CamelCloudConfigurationProperties properties;

    public ServiceDefinitionToZookeeperRegistration(CamelCloudConfigurationProperties properties) {
        this.properties = properties;
    }

    @Override
    public ZookeeperRegistration convert(ServiceDefinition source) {
        ZookeeperInstance instance = new ZookeeperInstance(
            source.getId(),
            source.getName(),
            source.getMetadata()
        );

        return ServiceInstanceRegistration.builder()
            .address(properties.getServiceRegistry().getServiceHost())
            .port(source.getPort())
            .name(source.getName())
            .payload(instance)
            .uriSpec(ZookeeperDiscoveryProperties.DEFAULT_URI_SPEC)
            .build();
    }
}
