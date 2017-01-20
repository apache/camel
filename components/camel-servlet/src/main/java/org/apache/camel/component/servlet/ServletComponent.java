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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.http.common.HttpBinding;
import org.apache.camel.http.common.HttpCommonComponent;
import org.apache.camel.http.common.HttpConsumer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.RestApiConsumerFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServletComponent extends HttpCommonComponent implements RestConsumerFactory, RestApiConsumerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ServletComponent.class);

    private String servletName = "CamelServlet";
    private HttpRegistry httpRegistry;
    private boolean attachmentMultipartBinding;

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
        HttpBinding binding = resolveAndRemoveReferenceParameter(parameters, "httpBinding", HttpBinding.class);
        Boolean matchOnUriPrefix = getAndRemoveParameter(parameters, "matchOnUriPrefix", Boolean.class);
        String servletName = getAndRemoveParameter(parameters, "servletName", String.class, getServletName());
        String httpMethodRestrict = getAndRemoveParameter(parameters, "httpMethodRestrict", String.class);
        HeaderFilterStrategy headerFilterStrategy = resolveAndRemoveReferenceParameter(parameters, "headerFilterStrategy", HeaderFilterStrategy.class);
        Boolean async = getAndRemoveParameter(parameters, "async", Boolean.class);
        Boolean attachmentMultipartBinding = getAndRemoveParameter(parameters, "attachmentMultipartBinding", Boolean.class);
        Boolean disableStreamCache = getAndRemoveParameter(parameters, "disableStreamCache", Boolean.class);

        if (lenientContextPath()) {
            // the uri must have a leading slash for the context-path matching to work with servlet, and it can be something people
            // forget to add and then the servlet consumer cannot match the context-path as would have been expected
            String scheme = ObjectHelper.before(uri, ":");
            String after = ObjectHelper.after(uri, ":");
            // rebuild uri to have exactly one leading slash
            while (after.startsWith("/")) {
                after = after.substring(1);
            }
            after = "/" + after;
            uri = scheme + ":" + after;
        }

        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        URI httpUri = URISupport.createRemainingURI(new URI(UnsafeUriCharactersEncoder.encodeHttpURI(uri)), parameters);

        ServletEndpoint endpoint = createServletEndpoint(uri, this, httpUri);
        endpoint.setServletName(servletName);
        if (async != null) {
            endpoint.setAsync(async);
        }
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
        if (attachmentMultipartBinding != null) {
            endpoint.setAttachmentMultipartBinding(attachmentMultipartBinding);
        } else {
            endpoint.setAttachmentMultipartBinding(isAttachmentMultipartBinding());
        }
        if (disableStreamCache != null) {
            endpoint.setDisableStreamCache(disableStreamCache);
        }

        // turn off stream caching if in attachment mode
        if (endpoint.isAttachmentMultipartBinding()) {
            if (disableStreamCache == null) {
                // disableStreamCache not explicit configured so we can automatic change it
                LOG.info("Disabling stream caching as attachmentMultipartBinding is enabled");
                endpoint.setDisableStreamCache(true);
            } else if (!disableStreamCache) {
                throw new IllegalArgumentException("The options attachmentMultipartBinding=true and disableStreamCache=false cannot work together."
                        + " Remove disableStreamCache to use AttachmentMultipartBinding");
            }
        }

        setProperties(endpoint, parameters);
        return endpoint;
    }

    /**
     * Whether defining the context-path is lenient and do not require an exact leading slash.
     */
    protected boolean lenientContextPath() {
        return true;
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

    public boolean isAttachmentMultipartBinding() {
        return attachmentMultipartBinding;
    }

    /**
     * Whether to automatic bind multipart/form-data as attachments on the Camel {@link Exchange}.
     * <p/>
     * The options attachmentMultipartBinding=true and disableStreamCache=false cannot work together.
     * Remove disableStreamCache to use AttachmentMultipartBinding.
     * <p/>
     * This is turn off by default as this may require servlet specific configuration to enable this when using Servlet's.
     */
    public void setAttachmentMultipartBinding(boolean attachmentMultipartBinding) {
        this.attachmentMultipartBinding = attachmentMultipartBinding;
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
            config = camelContext.getRestConfiguration("servlet", true);
        }

        Map<String, Object> map = new HashMap<String, Object>();
        // build query string, and append any endpoint configuration properties
        if (config.getComponent() == null || config.getComponent().equals("servlet")) {
            // setup endpoint options
            if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
                map.putAll(config.getEndpointProperties());
            }
        }

        boolean cors = config.isEnableCORS();
        if (cors) {
            // allow HTTP Options as we want to handle CORS in rest-dsl
            map.put("optionsEnabled", "true");
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
        if (cors) {
            restrict += ",OPTIONS";
        }
        // get the endpoint
        url = String.format(url, path, restrict);
        
        if (!query.isEmpty()) {
            url = url + "&" + query;
        }       

        ServletEndpoint endpoint = camelContext.getEndpoint(url, ServletEndpoint.class);
        setProperties(camelContext, endpoint, parameters);

        if (!map.containsKey("httpBindingRef")) {
            // use the rest binding, if not using a custom http binding
            HttpBinding binding = new ServletRestHttpBinding();
            binding.setHeaderFilterStrategy(endpoint.getHeaderFilterStrategy());
            binding.setTransferException(endpoint.isTransferException());
            binding.setEagerCheckContentAvailable(endpoint.isEagerCheckContentAvailable());
            endpoint.setHttpBinding(binding);
        }

        // configure consumer properties
        Consumer consumer = endpoint.createConsumer(processor);
        if (config.getConsumerProperties() != null && !config.getConsumerProperties().isEmpty()) {
            setProperties(camelContext, consumer, config.getConsumerProperties());
        }

        return consumer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        RestConfiguration config = getCamelContext().getRestConfiguration("servlet", true);
        // configure additional options on jetty configuration
        if (config.getComponentProperties() != null && !config.getComponentProperties().isEmpty()) {
            setProperties(this, config.getComponentProperties());
        }
    }
}