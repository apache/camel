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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceRegistry;
import org.apache.camel.impl.cloud.ServiceRegistryHelper;
import org.apache.camel.impl.cloud.ServiceRegistrySelectors;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

@Component("service")
public class ServiceComponent extends DefaultComponent {
    @Metadata(label = "advanced")
    private ServiceRegistry service;
    @Metadata(label = "advanced")
    private ServiceRegistry.Selector serviceSelector;

    public ServiceComponent() {
        this(null);
    }

    public ServiceComponent(CamelContext context) {
        super(context);

        this.serviceSelector = ServiceRegistrySelectors.DEFAULT_SELECTOR;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final String serviceName = StringHelper.before(remaining, ":");
        final String delegateUri = StringHelper.after(remaining, ":");

        ObjectHelper.notNull(serviceName, "Service Name");
        ObjectHelper.notNull(delegateUri, "Delegate URI");

        // Lookup the service registry, this may be a static selected service
        // or dynamically selected one through a ServiceRegistry.Selector
        final ServiceRegistry service = getServiceRegistry();

        // Compute service definition from parameters, this is used as default
        // definition
        final Map<String, String> params = new HashMap<>();

        for (Map.Entry<String, Object> entry: parameters.entrySet()) {
            if (!entry.getKey().startsWith(ServiceDefinition.SERVICE_META_PREFIX)) {
                continue;
            }

            final String key = entry.getKey();
            final String val = getCamelContext().getTypeConverter().convertTo(String.class, entry.getValue());

            params.put(key, val);
        }

        // add service name, this is always set from an uri path param
        params.put(ServiceDefinition.SERVICE_META_NAME, serviceName);

        // remove all the service related options so the underlying component
        // does not fail because of unknown parameters
        parameters.keySet().removeAll(params.keySet());

        return new ServiceEndpoint(
            uri,
            this,
            service,
            params,
            URISupport.appendParametersToURI(delegateUri, parameters)
        );
    }

    public ServiceRegistry getService() {
        return service;
    }

    /**
     * Inject the service to use.
     */
    public void setService(ServiceRegistry service) {
        this.service = service;
    }

    public ServiceRegistry.Selector getServiceSelector() {
        return serviceSelector;
    }

    /**
     *
     * Inject the service selector used to lookup the {@link ServiceRegistry} to use.
     */
    public void setServiceSelector(ServiceRegistry.Selector serviceSelector) {
        this.serviceSelector = serviceSelector;
    }

    // *****************
    // Helpers
    // *****************

    private ServiceRegistry getServiceRegistry() {
        if (service == null) {
            return ServiceRegistryHelper.lookupService(getCamelContext(), serviceSelector).orElseThrow(
                () -> new IllegalStateException("No cluster service found")
            );
        }

        return service;
    }
}
