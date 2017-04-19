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
package org.apache.camel.impl.cloud;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.cloud.ServiceChooser;
import org.apache.camel.cloud.ServiceChooserAware;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.cloud.ServiceDiscoveryAware;
import org.apache.camel.cloud.ServiceFilter;
import org.apache.camel.cloud.ServiceFilterAware;
import org.apache.camel.cloud.ServiceLoadBalancer;
import org.apache.camel.cloud.ServiceLoadBalancerFunction;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultServiceLoadBalancer
        extends ServiceSupport
        implements CamelContextAware, ServiceDiscoveryAware, ServiceChooserAware, ServiceFilterAware, ServiceLoadBalancer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultServiceLoadBalancer.class);

    private CamelContext camelContext;
    private ServiceDiscovery serviceDiscovery;
    private ServiceChooser serviceChooser;
    private ServiceFilter serviceFilter;

    public DefaultServiceLoadBalancer() {
    }

    // *************************************
    // Bean
    // *************************************

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public ServiceDiscovery getServiceDiscovery() {
        return serviceDiscovery;
    }

    @Override
    public void setServiceDiscovery(ServiceDiscovery serverDiscovery) {
        this.serviceDiscovery = serverDiscovery;
    }

    @Override
    public ServiceChooser getServiceChooser() {
        return serviceChooser;
    }

    @Override
    public void setServiceChooser(ServiceChooser serverChooser) {
        this.serviceChooser = serverChooser;
    }

    @Override
    public void setServiceFilter(ServiceFilter serviceFilter) {
        this.serviceFilter = serviceFilter;
    }

    @Override
    public ServiceFilter getServiceFilter() {
        return serviceFilter;
    }

    // *************************************
    // Lifecycle
    // *************************************

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "camel context");
        ObjectHelper.notNull(serviceDiscovery, "service discovery");
        ObjectHelper.notNull(serviceChooser, "service chooser");
        ObjectHelper.notNull(serviceFilter, "service serviceFilter");

        LOGGER.info("ServiceCall is using default load balancer with service discovery type: {}, service filter type: {} and service chooser type: {}",
            serviceDiscovery.getClass(),
            serviceFilter.getClass(),
            serviceChooser.getClass());

        ServiceHelper.startService(serviceChooser);
        ServiceHelper.startService(serviceDiscovery);
    }

    @Override
    protected void doStop() throws Exception {
        // Stop services if needed
        ServiceHelper.stopService(serviceDiscovery);
        ServiceHelper.stopService(serviceChooser);
    }

    // *************************************
    // Load Balancer
    // *************************************

    @Override
    public <T> T process(String serviceName, ServiceLoadBalancerFunction<T> function) throws Exception {
        ServiceDefinition service;

        List<ServiceDefinition> services = serviceDiscovery.getServices(serviceName);
        if (services == null || services.isEmpty()) {
            throw new RejectedExecutionException("No active services with name " + serviceName);
        } else {
            // filter services
            services = serviceFilter.apply(services);
            // let the client service chooser find which server to use
            service = services.size() > 1 ? serviceChooser.choose(services) : services.get(0);
            if (service == null) {
                throw new RejectedExecutionException("No active services with name " + serviceName);
            }
        }

        return function.apply(service);
    }
}
