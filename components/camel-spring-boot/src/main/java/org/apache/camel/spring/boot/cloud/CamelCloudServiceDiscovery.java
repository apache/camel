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
package org.apache.camel.spring.boot.cloud;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.impl.cloud.AggregatingServiceDiscovery;
import org.apache.camel.impl.cloud.CachingServiceDiscovery;

public class CamelCloudServiceDiscovery implements ServiceDiscovery {
    private ServiceDiscovery delegate;

    public CamelCloudServiceDiscovery(Long timeout, List<ServiceDiscovery> serviceDiscoveryList) {
        // Created a chained service discovery that collects services from multiple
        // ServiceDiscovery
        this.delegate = new AggregatingServiceDiscovery(serviceDiscoveryList);

        // If a timeout is provided, wrap the serviceDiscovery with a caching
        // strategy so the discovery implementations are not queried for each
        // discovery request
        if (timeout != null && timeout > 0) {
            this.delegate = CachingServiceDiscovery.wrap(this.delegate, timeout, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public List<ServiceDefinition> getServices(String name) {
        return delegate.getServices(name);
    }
}
