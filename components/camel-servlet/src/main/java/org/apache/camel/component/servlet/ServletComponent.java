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
package org.apache.camel.component.servlet;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.component.http.AuthMethod;
import org.apache.camel.component.http.HttpBinding;
import org.apache.camel.component.http.HttpClientConfigurer;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.component.http.HttpConsumer;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.params.HttpClientParams;

public class ServletComponent extends HttpComponent implements RestConsumerFactory {

    private String servletName = "CamelServlet";
    private HttpRegistry httpRegistry;

    public ServletComponent() {
        super(ServletEndpoint.class);
    }

    public ServletComponent(Class<? extends HttpEndpoint> endpointClass) {
        super(endpointClass);
    }


    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        HttpClientParams params = new HttpClientParams();
        IntrospectionSupport.setProperties(params, parameters, "httpClient.");

        // create the configurer to use for this endpoint
        final Set<AuthMethod> authMethods = new LinkedHashSet<AuthMethod>();
        HttpClientConfigurer configurer = createHttpClientConfigurer(parameters, authMethods);

        // must extract well known parameters before we create the endpoint
        Boolean throwExceptionOnFailure = getAndRemoveParameter(parameters, "throwExceptionOnFailure", Boolean.class);
        Boolean transferException = getAndRemoveParameter(parameters, "transferException", Boolean.class);
        Boolean bridgeEndpoint = getAndRemoveParameter(parameters, "bridgeEndpoint", Boolean.class);
        HttpBinding binding = resolveAndRemoveReferenceParameter(parameters, "httpBindingRef", HttpBinding.class);
        Boolean matchOnUriPrefix = getAndRemoveParameter(parameters, "matchOnUriPrefix", Boolean.class);
        String servletName = getAndRemoveParameter(parameters, "servletName", String.class, getServletName());
        String httpMethodRestrict = getAndRemoveParameter(parameters, "httpMethodRestrict", String.class);
        HeaderFilterStrategy headerFilterStrategy = resolveAndRemoveReferenceParameter(parameters, "headerFilterStrategy", HeaderFilterStrategy.class);

        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        URI httpUri = URISupport.createRemainingURI(new URI(UnsafeUriCharactersEncoder.encodeHttpURI(uri)), parameters);

        ServletEndpoint endpoint = createServletEndpoint(uri, this, httpUri, params, getHttpConnectionManager(), configurer);
        endpoint.setServletName(servletName);
        if (headerFilterStrategy != null) {
            endpoint.setHeaderFilterStrategy(headerFilterStrategy);
        } else {
            setEndpointHeaderFilterStrategy(endpoint);
        }

        // prefer to use endpoint configured over component configured
        if (binding == null) {
            // fallback to component configured
            binding = getHttpBinding();
        }
        if (binding != null) {
            endpoint.setBinding(binding);
        }
        // should we use an exception for failed error codes?
        if (throwExceptionOnFailure != null) {
            endpoint.setThrowExceptionOnFailure(throwExceptionOnFailure);
        }
        // should we transfer exception as serialized object
        if (transferException != null) {
            endpoint.setTransferException(transferException);
        }
        if (bridgeEndpoint != null) {
            endpoint.setBridgeEndpoint(bridgeEndpoint);
        }
        if (matchOnUriPrefix != null) {
            endpoint.setMatchOnUriPrefix(matchOnUriPrefix);
        }
        if (httpMethodRestrict != null) {
            endpoint.setHttpMethodRestrict(httpMethodRestrict);
        }

        setProperties(endpoint, parameters);
        return endpoint;
    }

    /**
     * Strategy to create the servlet endpoint.
     */
    protected ServletEndpoint createServletEndpoint(String endpointUri, ServletComponent component, URI httpUri, HttpClientParams params,
                                                    HttpConnectionManager httpConnectionManager, HttpClientConfigurer clientConfigurer) throws Exception {
        return new ServletEndpoint(endpointUri, component, httpUri, params, httpConnectionManager, clientConfigurer);
    }

    @Override
    public void connect(HttpConsumer consumer) throws Exception {
        ServletConsumer sc = (ServletConsumer) consumer;
        String name = sc.getEndpoint().getServletName();
        HttpRegistry registry = httpRegistry;
        if (registry == null) {
            registry = DefaultHttpRegistry.getHttpRegistry(name);
        }
        registry.register(consumer);
    }

    @Override
    public void disconnect(HttpConsumer consumer) throws Exception {
        ServletConsumer sc = (ServletConsumer) consumer;
        String name = sc.getEndpoint().getServletName();
        HttpRegistry registry = httpRegistry;
        if (registry == null) {
            registry = DefaultHttpRegistry.getHttpRegistry(name);
        }
        registry.unregister(consumer);
    }

    public String getServletName() {
        return servletName;
    }

    public void setServletName(String servletName) {
        this.servletName = servletName;
    }

    public HttpRegistry getHttpRegistry() {
        return httpRegistry;
    }

    public void setHttpRegistry(HttpRegistry httpRegistry) {
        this.httpRegistry = httpRegistry;
    }

    @Override
    public Consumer createConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                                   String consumes, String produces, Map<String, Object> parameters) throws Exception {
        String path = basePath;
        if (uriTemplate != null) {
            // make sure to avoid double slashes
            if (uriTemplate.startsWith("/")) {
                path = path + uriTemplate;
            } else {
                path = path + "/" + uriTemplate;
            }
        }
        path = FileUtil.stripLeadingSeparator(path);

        // if no explicit port/host configured, then use port from rest configuration
        RestConfiguration config = getCamelContext().getRestConfiguration();

        Map<String, Object> map = new HashMap<String, Object>();
        // build query string, and append any endpoint configuration properties
        if (config.getComponent() == null || config.getComponent().equals("servlet")) {
            // setup endpoint options
            if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
                map.putAll(config.getEndpointProperties());
            }
        }

        String query = URISupport.createQueryString(map);

        String url = "servlet:///%s?httpMethodRestrict=%s";
        // must use upper case for restrict
        String restrict = verb.toUpperCase(Locale.US);

        // get the endpoint
        url = String.format(url, path, restrict);
        
        if (!query.isEmpty()) {
            url = url + "&" + query;
        }       
        ServletEndpoint endpoint = camelContext.getEndpoint(url, ServletEndpoint.class);
        setProperties(endpoint, parameters);

        // use the rest binding
        HttpBinding binding = new ServletRestHttpBinding();
        binding.setHeaderFilterStrategy(endpoint.getHeaderFilterStrategy());
        binding.setTransferException(endpoint.isTransferException());
        binding.setEagerCheckContentAvailable(endpoint.isEagerCheckContentAvailable());
        endpoint.setBinding(binding);

        // configure consumer properties
        Consumer consumer = endpoint.createConsumer(processor);
        if (config != null && config.getConsumerProperties() != null && !config.getConsumerProperties().isEmpty()) {
            setProperties(consumer, config.getConsumerProperties());
        }

        return consumer;
    }
}