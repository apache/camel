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
package org.apache.camel.component.rest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;
import org.apache.camel.StaticService;
import org.apache.camel.ValueHolder;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

public class DefaultRestRegistry extends ServiceSupport implements StaticService, RestRegistry, CamelContextAware {

    private CamelContext camelContext;
    private final Map<Consumer, RestService> registry = new LinkedHashMap<>();
    private transient Producer apiProducer;

    @Override
    public void addRestService(Consumer consumer, String url, String baseUrl, String basePath, String uriTemplate, String method,
                               String consumes, String produces, String inType, String outType, String routeId, String description) {
        RestServiceEntry entry = new RestServiceEntry(consumer, url, baseUrl, basePath, uriTemplate, method, consumes, produces, inType, outType, routeId, description);
        registry.put(consumer, entry);
    }

    @Override
    public void removeRestService(Consumer consumer) {
        registry.remove(consumer);
    }

    @Override
    public List<RestRegistry.RestService> listAllRestServices() {
        return new ArrayList<>(registry.values());
    }

    @Override
    public int size() {
        return registry.size();
    }

    @Override
    public String apiDocAsJson() {
        // see if there is a rest-api endpoint which would be the case if rest api-doc has been explicit enabled
        if (apiProducer == null) {
            Endpoint restApiEndpoint = null;
            Endpoint restEndpoint = null;
            for (Map.Entry<? extends ValueHolder<String>, Endpoint> entry : camelContext.getEndpointRegistry().entrySet()) {
                String uri = entry.getKey().get();
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
                String componentName = rest.getProducerComponentName();

                if (componentName != null) {
                    RestConfiguration config = camelContext.getRestConfiguration();

                    String apiComponent = config.getApiComponent() != null ? config.getApiComponent() : RestApiEndpoint.DEFAULT_API_COMPONENT_NAME;
                    String path = config.getApiContextPath() != null ? config.getApiContextPath() : "api-doc";
                    String uri = String.format(
                        "rest-api:%s/%s?componentName=%s&apiComponentName=%s&contextIdPattern=#name#",
                        path, camelContext.getName(), componentName, apiComponent);

                    restApiEndpoint = camelContext.getEndpoint(uri);
                }
            }

            if (restApiEndpoint != null) {
                // reuse the producer to avoid creating it
                try {
                    apiProducer = restApiEndpoint.createProducer();
                    camelContext.addService(apiProducer, true);
                } catch (Exception e) {
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                }
            }
        }

        if (apiProducer != null) {
            try {
                Exchange dummy = apiProducer.getEndpoint().createExchange();
                apiProducer.process(dummy);

                return dummy.getMessage().getBody(String.class);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        return null;
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
        ObjectHelper.notNull(camelContext, "camelContext", this);
        // add a lifecycle so we can keep track when consumers is being removed, so we can unregister them from our registry
        camelContext.addLifecycleStrategy(new RemoveRestServiceLifecycleStrategy());
    }

    @Override
    protected void doStop() throws Exception {
        registry.clear();
    }

    /**
     * Represents a rest service
     */
    private final class RestServiceEntry implements RestService {

        private final Consumer consumer;
        private final String url;
        private final String baseUrl;
        private final String basePath;
        private final String uriTemplate;
        private final String method;
        private final String consumes;
        private final String produces;
        private final String inType;
        private final String outType;
        private final String routeId;
        private final String description;

        private RestServiceEntry(Consumer consumer, String url, String baseUrl, String basePath, String uriTemplate, String method,
                                 String consumes, String produces, String inType, String outType, String routeId, String description) {
            this.consumer = consumer;
            this.url = url;
            this.baseUrl = baseUrl;
            this.basePath = basePath;
            this.uriTemplate = uriTemplate;
            this.method = method;
            this.consumes = consumes;
            this.produces = produces;
            this.inType = inType;
            this.outType = outType;
            this.routeId = routeId;
            this.description = description;
        }

        @Override
        public Consumer getConsumer() {
            return consumer;
        }

        @Override
        public String getUrl() {
            return url;
        }

        @Override
        public String getBaseUrl() {
            return baseUrl;
        }

        @Override
        public String getBasePath() {
            return basePath;
        }

        @Override
        public String getUriTemplate() {
            return uriTemplate;
        }

        @Override
        public String getMethod() {
            return method;
        }

        @Override
        public String getConsumes() {
            return consumes;
        }

        @Override
        public String getProduces() {
            return produces;
        }

        @Override
        public String getInType() {
            return inType;
        }

        @Override
        public String getOutType() {
            return outType;
        }

        @Override
        public String getState() {
            // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
            ServiceStatus status = null;
            if (consumer instanceof StatefulService) {
                status = ((StatefulService) consumer).getStatus();
            }
            // if no status exists then its stopped
            if (status == null) {
                status = ServiceStatus.Stopped;
            }
            return status.name();
        }

        @Override
        public String getRouteId() {
            return routeId;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    /**
     * A {@link org.apache.camel.spi.LifecycleStrategy} that keeps track when a {@link Consumer} is removed
     * and automatic un-register it from this REST registry.
     */
    private final class RemoveRestServiceLifecycleStrategy extends LifecycleStrategySupport {

        @Override
        public void onServiceRemove(CamelContext context, Service service, Route route) {
            super.onServiceRemove(context, service, route);

            // if its a consumer then de-register it from the rest registry
            if (service instanceof Consumer) {
                removeRestService((Consumer) service);
            }
        }
    }
}
