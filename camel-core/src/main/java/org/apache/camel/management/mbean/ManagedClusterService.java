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
package org.apache.camel.management.mbean;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;
import org.apache.camel.api.management.mbean.ManagedClusterServiceMBean;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.impl.cluster.ClusterServiceHelper;
import org.apache.camel.spi.ManagementStrategy;

public class ManagedClusterService implements ManagedClusterServiceMBean {
    private final CamelContext context;
    private final CamelClusterService service;

    public ManagedClusterService(CamelContext context, CamelClusterService service) {
        this.context = context;
        this.service = service;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public CamelContext getContext() {
        return context;
    }

    public CamelClusterService getService() {
        return service;
    }

    @Override
    public void start() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        service.start();
    }

    @Override
    public void stop() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        service.stop();
    }

    @Override
    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        if (service instanceof StatefulService) {
            ServiceStatus status = ((StatefulService) service).getStatus();
            return status.name();
        }

        // assume started if not a ServiceSupport instance
        return ServiceStatus.Started.name();
    }

    @Override
    public String getCamelId() {
        return context.getName();
    }

    @Override
    public Collection<String> getNamespaces() {
        return ClusterServiceHelper.lookupService(context)
            .map(CamelClusterService::getNamespaces)
            .orElseGet(Collections::emptyList);
    }

    @Override
    public void startView(String namespace) throws Exception {
        Optional<CamelClusterService> service = ClusterServiceHelper.lookupService(context);
        if (service.isPresent()) {
            service.get().startView(namespace);
        }
    }

    @Override
    public void stopView(String namespace) throws Exception {
        Optional<CamelClusterService> service = ClusterServiceHelper.lookupService(context);
        if (service.isPresent()) {
            service.get().stopView(namespace);
        }
    }

    @Override
    public boolean isLeader(String namespace) {
        return ClusterServiceHelper.lookupService(context)
            .map(s -> s.isLeader(namespace))
            .orElse(false);
    }
}
