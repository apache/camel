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
package org.apache.camel.spring.cloud;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.impl.cloud.DefaultServiceDefinition;
import org.springframework.cloud.client.discovery.DiscoveryClient;

public class CamelSpringCloudServiceDiscovery implements ServiceDiscovery {
    private final DiscoveryClient discoveryClient;

    public CamelSpringCloudServiceDiscovery(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @Override
    public List<ServiceDefinition> getServices(String name) {
        return discoveryClient.getInstances(name).stream()
            .map(
                si -> {
                    return DefaultServiceDefinition.builder()
                        .withName(si.getServiceId())
                        .withHost(si.getHost())
                        .withPort(si.getPort())
                        .withId(name)
                        .withMeta(si.getMetadata())
                        .build();
                }
            ).collect(Collectors.toList());
    }
}
