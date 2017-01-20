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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.cloud.LoadBalancer;
import org.apache.camel.cloud.LoadBalancerFunction;
import org.apache.camel.impl.cloud.DefaultServiceDefinition;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;

public class CamelCloudLoadBalancer extends ServiceSupport implements CamelContextAware, LoadBalancer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CamelCloudLoadBalancer.class);

    private final LoadBalancerClient loadBalancerClient;
    private CamelContext camelContext;

    public CamelCloudLoadBalancer(LoadBalancerClient loadBalancerClient) {
        this.loadBalancerClient = loadBalancerClient;
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

        LOGGER.info("ServiceCall is using cloud load balancer of type: {}",
            loadBalancerClient.getClass());
    }

    @Override
    protected void doStop() throws Exception {
    }

    @Override
    public <T> T process(String serviceName, LoadBalancerFunction<T> function) throws Exception {
        return loadBalancerClient.execute(
            serviceName,
            i -> function.apply(new DefaultServiceDefinition(i.getServiceId(), i.getHost(), i.getPort(), i.getMetadata()))
        );
    }
}