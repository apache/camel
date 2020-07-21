/*
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
package org.apache.camel.component.service;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.StartupListener;
import org.apache.camel.SuspendableService;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceRegistry;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Managed Service Consumer")
public class ServiceConsumer extends DefaultConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(ServiceConsumer.class);

    private final ServiceRegistry serviceRegistry;
    private final Endpoint delegatedEndpoint;
    private final Processor processor;
    private Consumer delegatedConsumer;

    public ServiceConsumer(ServiceEndpoint serviceEndpoint, Processor processor, ServiceRegistry serviceRegistry) {
        super(serviceEndpoint, processor);

        this.serviceRegistry = serviceRegistry;
        this.delegatedEndpoint = serviceEndpoint.getEndpoint();
        this.processor = processor;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        final ServiceEndpoint endpoint = (ServiceEndpoint)getEndpoint();
        final ServiceDefinition definition = endpoint.getServiceDefinition();

        LOG.debug("Using ServiceRegistry instance {} (id={}, type={}) to register: {}",
            serviceRegistry,
            serviceRegistry.getId(),
            serviceRegistry.getClass().getName(),
            definition
        );

        // register service
        serviceRegistry.register(definition);

        // start delegate
        delegatedConsumer = delegatedEndpoint.createConsumer(processor);
        if (delegatedConsumer instanceof StartupListener) {
            getEndpoint().getCamelContext().addStartupListener((StartupListener) delegatedConsumer);
        }

        ServiceHelper.startService(delegatedEndpoint);
        ServiceHelper.startService(delegatedConsumer);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        final ServiceEndpoint endpoint = (ServiceEndpoint)getEndpoint();
        final ServiceDefinition definition = endpoint.getServiceDefinition();

        // de-register service
        serviceRegistry.deregister(definition);

        // stop delegate
        ServiceHelper.stopAndShutdownServices(delegatedConsumer);
        ServiceHelper.stopAndShutdownServices(delegatedEndpoint);

        delegatedConsumer = null;
    }

    @Override
    protected void doResume() throws Exception {
        if (delegatedConsumer instanceof SuspendableService) {
            final ServiceEndpoint endpoint = (ServiceEndpoint)getEndpoint();
            final ServiceDefinition definition = endpoint.getServiceDefinition();

            // register service
            serviceRegistry.register(definition);

            ((SuspendableService)delegatedConsumer).resume();
        }
        super.doResume();
    }

    @Override
    protected void doSuspend() throws Exception {
        if (delegatedConsumer instanceof SuspendableService) {
            final ServiceEndpoint endpoint = (ServiceEndpoint)getEndpoint();
            final ServiceDefinition definition = endpoint.getServiceDefinition();

            // de-register service
            serviceRegistry.deregister(definition);

            ((SuspendableService)delegatedConsumer).suspend();
        }
        super.doSuspend();
    }
}
