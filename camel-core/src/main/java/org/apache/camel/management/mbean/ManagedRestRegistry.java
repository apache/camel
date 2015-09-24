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

import java.util.List;
import java.util.Map;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedRestRegistryMBean;
import org.apache.camel.component.rest.RestApiEndpoint;
import org.apache.camel.component.rest.RestEndpoint;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.util.ObjectHelper;

/**
 *
 */
@ManagedResource(description = "Managed RestRegistry")
public class ManagedRestRegistry extends ManagedService implements ManagedRestRegistryMBean {

    private final RestRegistry registry;
    private transient Producer apiProducer;

    public ManagedRestRegistry(CamelContext context, RestRegistry registry) {
        super(context, registry);
        this.registry = registry;
    }

    public RestRegistry getRegistry() {
        return registry;
    }

    @Override
    public int getNumberOfRestServices() {
        return registry.size();
    }

    @Override
    public TabularData listRestServices() {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.listRestServicesTabularType());
            List<RestRegistry.RestService> services = registry.listAllRestServices();
            for (RestRegistry.RestService entry : services) {
                CompositeType ct = CamelOpenMBeanTypes.listRestServicesCompositeType();
                String url = entry.getUrl();
                String baseUrl = entry.getBaseUrl();
                String basePath = entry.getBasePath();
                String uriTemplate = entry.getUriTemplate();
                String method = entry.getMethod();
                String consumes = entry.getConsumes();
                String produces = entry.getProduces();
                String state = entry.getState();
                String inType = entry.getInType();
                String outType = entry.getOutType();
                String routeId = entry.getRouteId();
                String description = entry.getDescription();

                CompositeData data = new CompositeDataSupport(ct, new String[]
                {"url", "baseUrl", "basePath", "uriTemplate", "method", "consumes", "produces", "inType", "outType", "state", "routeId", "description"},
                        new Object[]{url, baseUrl, basePath, uriTemplate, method, consumes, produces, inType, outType, state, routeId, description});
                answer.put(data);
            }
            return answer;
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public String apiDocAsJson() {
        // see if there is a rest-api endpoint which would be the case if rest api-doc has been explicit enabled
        if (apiProducer == null) {
            Endpoint restApiEndpoint = null;
            Endpoint restEndpoint = null;
            for (Map.Entry<String, Endpoint> entry : getContext().getEndpointMap().entrySet()) {
                String uri = entry.getKey();
                if (uri.startsWith("rest-api:")) {
                    restApiEndpoint = entry.getValue();
                    break;
                } else if (restEndpoint == null && uri.startsWith("rest:")) {
                    restEndpoint = entry.getValue();
                }
            }

            if (restApiEndpoint == null && restEndpoint != null) {
                // no rest-api has been explicit enabled, then we need to create it first
                RestEndpoint rest = (RestEndpoint) restEndpoint;
                String componentName = rest.getComponentName();

                if (componentName != null) {
                    RestConfiguration config = getContext().getRestConfiguration(componentName, true);
                    String apiComponent = config.getApiComponent() != null ? config.getApiComponent() : RestApiEndpoint.DEFAULT_API_COMPONENT_NAME;
                    String path = config.getApiContextPath() != null ? config.getApiContextPath() : "api-doc";
                    restApiEndpoint = getContext().getEndpoint(String.format("rest-api:%s/%s?componentName=%s&apiComponentName=%s&contextIdPattern=#name#", path, getCamelId(), componentName, apiComponent));
                }
            }

            if (restApiEndpoint != null) {
                // reuse the producer to avoid creating it
                try {
                    apiProducer = restApiEndpoint.createProducer();
                    getContext().addService(apiProducer, true);
                } catch (Exception e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
            }
        }

        if (apiProducer != null) {
            try {
                Exchange dummy = apiProducer.getEndpoint().createExchange();
                apiProducer.process(dummy);

                String json = dummy.hasOut() ? dummy.getOut().getBody(String.class) : dummy.getIn().getBody(String.class);
                return json;
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }

        return null;
    }

}
