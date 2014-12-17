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
package org.apache.camel.component.rest;

import java.util.Map;
import java.util.Set;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.HostUtils;
import org.apache.camel.util.ObjectHelper;

@UriEndpoint(scheme = "rest")
public class RestEndpoint extends DefaultEndpoint {

    @UriParam
    private String method;
    @UriParam
    private String path;
    @UriParam
    private String uriTemplate;
    @UriParam
    private String consumes;
    @UriParam
    private String produces;
    @UriParam
    private String componentName;
    @UriParam
    private String inType;
    @UriParam
    private String outType;
    @UriParam
    private String routeId;
    @UriParam
    private String description;

    private Map<String, Object> parameters;

    public RestEndpoint(String endpointUri, RestComponent component) {
        super(endpointUri, component);
    }

    @Override
    public RestComponent getComponent() {
        return (RestComponent) super.getComponent();
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getUriTemplate() {
        return uriTemplate;
    }

    public void setUriTemplate(String uriTemplate) {
        this.uriTemplate = uriTemplate;
    }

    public String getConsumes() {
        return consumes;
    }

    public void setConsumes(String consumes) {
        this.consumes = consumes;
    }

    public String getProduces() {
        return produces;
    }

    public void setProduces(String produces) {
        this.produces = produces;
    }

    public String getComponentName() {
        return componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    public String getInType() {
        return inType;
    }

    public void setInType(String inType) {
        this.inType = inType;
    }

    public String getOutType() {
        return outType;
    }

    public void setOutType(String outType) {
        this.outType = outType;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Producer not supported");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        RestConsumerFactory factory = null;

        if (getComponentName() != null) {
            Object comp = getCamelContext().getRegistry().lookupByName(getComponentName());
            if (comp != null && comp instanceof RestConsumerFactory) {
                factory = (RestConsumerFactory) comp;
            } else {
                comp = getCamelContext().getComponent(getComponentName());
                if (comp != null && comp instanceof RestConsumerFactory) {
                    factory = (RestConsumerFactory) comp;
                }
            }

            if (factory == null) {
                if (comp != null) {
                    throw new IllegalArgumentException("Component " + getComponentName() + " is not a RestConsumerFactory");
                } else {
                    throw new NoSuchBeanException(getComponentName(), RestConsumerFactory.class.getName());
                }
            }
        }

        // try all components
        if (factory == null) {
            for (String name : getCamelContext().getComponentNames()) {
                Component comp = getCamelContext().getComponent(name);
                if (comp != null && comp instanceof RestConsumerFactory) {
                    factory = (RestConsumerFactory) comp;
                    break;
                }
            }
        }

        // lookup in registry
        if (factory == null) {
            Set<RestConsumerFactory> factories = getCamelContext().getRegistry().findByType(RestConsumerFactory.class);
            if (factories != null && factories.size() == 1) {
                factory = factories.iterator().next();
            }
        }

        if (factory != null) {
            Consumer consumer = factory.createConsumer(getCamelContext(), processor, getMethod(), getPath(), getUriTemplate(), getConsumes(), getProduces(), getParameters());
            configureConsumer(consumer);

            // if no explicit port/host configured, then use port from rest configuration
            String scheme = "http";
            String host = "";
            int port = 80;

            RestConfiguration config = getCamelContext().getRestConfiguration();
            if (config.getScheme() != null) {
                scheme = config.getScheme();
            }
            if (config.getHost() != null) {
                host = config.getHost();
            }
            int num = config.getPort();
            if (num > 0) {
                port = num;
            }

            // if no explicit hostname set then resolve the hostname
            if (ObjectHelper.isEmpty(host)) {
                if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.localHostName) {
                    host = HostUtils.getLocalHostName();
                } else if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.localIp) {
                    host = HostUtils.getLocalIp();
                }
            }


            // calculate the url to the rest service
            String path = getPath();
            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            // there may be an optional context path configured to help Camel calculate the correct urls for the REST services
            // this may be needed when using camel-serlvet where we cannot get the actual context-path or port number of the servlet engine
            // during init of the servlet
            String contextPath = config.getContextPath();
            if (contextPath != null) {
                if (!contextPath.startsWith("/")) {
                    path = "/" + contextPath + path;
                } else {
                    path = contextPath + path;
                }
            }

            String baseUrl = scheme + "://" + host + (port != 80 ? ":" + port : "") + path;

            String url = baseUrl;
            if (uriTemplate != null) {
                // make sure to avoid double slashes
                if (uriTemplate.startsWith("/")) {
                    url = url + uriTemplate;
                } else {
                    url = url + "/" + uriTemplate;
                }
            }

            // add to rest registry so we can keep track of them, we will remove from the registry when the consumer is removed
            // the rest registry will automatic keep track when the consumer is removed,
            // and un-register the REST service from the registry
            getCamelContext().getRestRegistry().addRestService(consumer, url, baseUrl, getPath(), getUriTemplate(), getMethod(),
                    getConsumes(), getProduces(), getInType(), getOutType(), getRouteId(), getDescription());
            return consumer;
        } else {
            throw new IllegalStateException("Cannot find RestConsumerFactory in Registry or as a Component to use");
        }
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }
}
