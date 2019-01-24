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

import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.cloud.ServiceLoadBalancer;
import org.apache.camel.cloud.ServiceLoadBalancerFunction;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;

public class CamelSpringCloudServiceLoadBalancer extends ServiceSupport implements CamelContextAware, ServiceLoadBalancer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CamelSpringCloudServiceLoadBalancer.class);

    private final LoadBalancerClient loadBalancerClient;
    private final ServiceLoadBalancer loadBalancer;
    private CamelContext camelContext;

    public CamelSpringCloudServiceLoadBalancer(LoadBalancerClient loadBalancerClient, Optional<LoadBalancerClientAdapter> clientAdapter) {
        this.loadBalancerClient = loadBalancerClient;
        this.loadBalancer = clientAdapter.orElseGet(DefaultLoadBalancerClientAdapter::new).adapt(loadBalancerClient);
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "camelContext");
        ObjectHelper.notNull(loadBalancerClient, "loadBalancerClient");

        LOGGER.info("ServiceCall is using cloud load balancer of type: {}", loadBalancerClient.getClass());
    }

    @Override
    protected void doStop() throws Exception {
    }

    @Override
    public <T> T process(String serviceName, ServiceLoadBalancerFunction<T> function) throws Exception {
        return loadBalancer.process(serviceName, function);
    }

    // *******************************
    //
    // *******************************

    @FunctionalInterface
    public interface LoadBalancerClientAdapter {
        ServiceLoadBalancer adapt(LoadBalancerClient client);
    }
}