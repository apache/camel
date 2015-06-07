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
package org.apache.camel.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consumer;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;
import org.apache.camel.StaticService;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

public class DefaultRestRegistry extends ServiceSupport implements StaticService, RestRegistry, CamelContextAware {

    private CamelContext camelContext;
    private final Map<Consumer, RestService> registry = new LinkedHashMap<Consumer, RestService>();

    public void addRestService(Consumer consumer, String url, String baseUrl, String basePath, String uriTemplate, String method,
                               String consumes, String produces, String inType, String outType, String routeId, String description) {
        RestServiceEntry entry = new RestServiceEntry(consumer, url, baseUrl, basePath, uriTemplate, method, consumes, produces, inType, outType, routeId, description);
        registry.put(consumer, entry);
    }

    public void removeRestService(Consumer consumer) {
        registry.remove(consumer);
    }

    @Override
    public List<RestRegistry.RestService> listAllRestServices() {
        return new ArrayList<RestService>(registry.values());
    }

    @Override
    public int size() {
        return registry.size();
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

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

        public Consumer getConsumer() {
            return consumer;
        }

        public String getUrl() {
            return url;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getBasePath() {
            return basePath;
        }

        public String getUriTemplate() {
            return uriTemplate;
        }

        public String getMethod() {
            return method;
        }

        public String getConsumes() {
            return consumes;
        }

        public String getProduces() {
            return produces;
        }

        public String getInType() {
            return inType;
        }

        public String getOutType() {
            return outType;
        }

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

        public String getRouteId() {
            return routeId;
        }

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
