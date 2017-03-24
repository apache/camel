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
package org.apache.camel.spring.cloud.netflix;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ServerList;
import org.apache.camel.spring.boot.cloud.CamelCloudServiceDiscovery;
import org.apache.camel.spring.boot.cloud.CamelCloudServiceFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CamelCloudNetflixRibbonClientConfiguration {
    @Autowired
    private IClientConfig clientConfig;
    @Autowired
    private CamelCloudServiceDiscovery serviceDiscovery;
    @Autowired
    private CamelCloudServiceFilter serviceFilter;

    @Bean
    @ConditionalOnMissingBean
    public ServerList<?> ribbonServerList() {
        CamelCloudNetflixServerList serverList = new CamelCloudNetflixServerList();
        serverList.setServiceDiscovery(serviceDiscovery);
        serverList.setServiceFilter(serviceFilter);
        serverList.initWithNiwsConfig(clientConfig);

        return serverList;
    }
}
