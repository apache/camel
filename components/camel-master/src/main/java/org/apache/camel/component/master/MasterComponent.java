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
package org.apache.camel.component.master;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.cluster.ClusterServiceHelper;
import org.apache.camel.support.cluster.ClusterServiceSelectors;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * The master camel component provides a way to ensures that only a single endpoint
 * in a cluster is active at any point in time with automatic failover if the
 * JVM dies or the leadership is lot for any reason.
 * <p>
 * This feature is useful if you need to consume from a backend that does not
 * support concurrent consumption.
 */
@Component("master")
public class MasterComponent extends DefaultComponent {
    @Metadata(label = "advanced")
    private CamelClusterService service;
    @Metadata(label = "advanced")
    private CamelClusterService.Selector serviceSelector;

    public MasterComponent() {
        this(null);
    }

    public MasterComponent(CamelContext context) {
        super(context);

        this.serviceSelector = ClusterServiceSelectors.DEFAULT_SELECTOR;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> params) throws Exception {
        // we are registering a regular endpoint
        String namespace = StringHelper.before(remaining, ":");
        String delegateUri = StringHelper.after(remaining, ":");

        if (ObjectHelper.isEmpty(namespace) || ObjectHelper.isEmpty(delegateUri)) {
            throw new IllegalArgumentException("Wrong uri syntax : master:namespace:uri, got " + remaining);
        }

        // we need to apply the params here
        if (params != null && params.size() > 0) {
            delegateUri = delegateUri + "?" + uri.substring(uri.indexOf('?') + 1);
        }

        return new MasterEndpoint(
            uri,
            this,
            getClusterService(),
            namespace,
            delegateUri
        );
    }

    @Override
    public boolean useRawUri() {
        // disable URI encoding at master endpoint level to avoid encoding URI twice
        return true;
    }

    public CamelClusterService getService() {
        return service;
    }

    /**
     * Inject the service to use.
     */
    public void setService(CamelClusterService service) {
        this.service = service;
    }

    public CamelClusterService.Selector getServiceSelector() {
        return serviceSelector;
    }

    /**
     *
     * Inject the service selector used to lookup the {@link CamelClusterService} to use.
     */
    public void setServiceSelector(CamelClusterService.Selector serviceSelector) {
        this.serviceSelector = serviceSelector;
    }

    // ********************************
    // Helpers
    // ********************************

    private CamelClusterService getClusterService() throws Exception {
        if (service == null) {
            CamelContext context = getCamelContext();

            ObjectHelper.notNull(context, "Camel Context");

            service = ClusterServiceHelper.lookupService(context, serviceSelector).orElseThrow(
                () -> new IllegalStateException("No cluster service found")
            );
        }

        return service;
    }
}
