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
import java.util.Locale;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.http.common.HttpBinding;
import org.apache.camel.http.common.HttpCommonComponent;
import org.apache.camel.http.common.HttpConsumer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.RestApiConsumerFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

public class ServletComponent extends HttpCommonComponent implements RestConsumerFactory, RestApiConsumerFactory {

    private String servletName = "CamelServlet";
    private HttpRegistry httpRegistry;

    public ServletComponent() {
        super(ServletEndpoint.class);
    }

    public ServletComponent(Class<? extends ServletEndpoint> endpointClass) {
        super(endpointClass);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // must extract well known parameters before we create the endpoint
        Boolean throwExceptionOnFailure = getAndRemoveParameter(parameters, "throwExceptionOnFailure", Boolean.class);
        Boolean transferException = getAndRemoveParameter(parameters, "transferException", Boolean.class);
        Boolean bridgeEndpoint = getAndRemoveParameter(parameters, "bridgeEndpoint", Boolean.class);
        // TODO we need to remove the Ref in Camel 3.0
        HttpBinding binding = resolveAndRemoveReferenceParameter(parameters, "httpBindingRef", HttpBinding.class);
        if (binding == null) {
            // just check the httpBinding parameter
            binding = resolveAndRemoveReferenceParameter(parameters, "httpBinding", HttpBinding.class);
        }
        Boolean matchOnUriPrefix = getAndRemoveParameter(parameters, "matchOnUriPrefix", Boolean.class);
        String servletName = getAndRemoveParameter(parameters, "servletName", String.class, getServletName());
        String httpMethodRestrict = getAndRemoveParameter(parameters, "httpMethodRestrict", String.class);
        HeaderFilterStrategy headerFilterStrategy = resolveAndRemoveReferenceParameter(parameters, "headerFilterStrategy", HeaderFilterStrategy.class);

        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        URI httpUri = URISupport.createRemainingURI(new URI(UnsafeUriCharactersEncoder.encodeHttpURI(uri)), parameters);

        ServletEndpoint endpoint = createServletEndpoint(uri, this, httpUri);
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
    protected ServletEndpoint createServletEndpoint(String endpointUri, ServletComponent component, URI httpUri) throws Exception {
        return new ServletEndpoint(endpointUri, component, httpUri);
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

    /**
     * Default name of servlet to use. The default name is <tt>CamelServlet</tt>.
     */
    public void setServletName(String servletName) {
        this.servletName = servletName;
    }

    public HttpRegistry getHttpRegistry() {
        return httpRegistry;
    }

    /**
     * To use a custom {@link org.apache.camel.component.servlet.HttpRegistry}.
     */
    public void setHttpRegistry(HttpRegistry httpRegistry) {
        this.httpRegistry = httpRegistry;
    }

    @Override
    public Consumer createConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                                   String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters) throws Exception {
        return doCreateConsumer(camelContext, processor, verb, basePath, uriTemplate, consumes, produces, configuration, parameters, false);
    }

    @Override
    public Consumer createApiConsumer(CamelContext camelContext, Processor processor, String contextPath,
                                      RestConfiguration configuration, Map<String, Object> parameters) throws Exception {
        // reuse the createConsumer method we already have. The api need to use GET and match on uri prefix
        return doCreateConsumer(camelContext, processor, "GET", contextPath, null, null, null, configuration, parameters, true);
    }

    Consumer doCreateConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                              String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters, boolean api) throws Exception {

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
        RestConfiguration config = configuration;
        if (config == null) {
            config = getCamelContext().getRestConfiguration("servlet", true);
        }

        Map<String, Object> map = new HashMap<String, Object>();
        // setup endpoint options
        if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
            map.putAll(config.getEndpointProperties());
        }

        // do not append with context-path as the servlet path should be without context-path

        String query = URISupport.createQueryString(map);

        String url;
        if (api) {
            url = "servlet:///%s?matchOnUriPrefix=true&httpMethodRestrict=%s";
        } else {
            url = "servlet:///%s?httpMethodRestrict=%s";
        }
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
        if (config.getConsumerProperties() != null && !config.getConsumerProperties().isEmpty()) {
            setProperties(consumer, config.getConsumerProperties());
        }

        return consumer;
    }
}