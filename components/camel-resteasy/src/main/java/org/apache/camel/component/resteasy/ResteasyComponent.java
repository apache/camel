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
package org.apache.camel.component.resteasy;

import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.http.common.DefaultHttpRegistry;
import org.apache.camel.http.common.HttpConsumer;
import org.apache.camel.http.common.HttpRegistry;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

@Component("resteasy")
@Metadata(excludeProperties = "clientConnectionManager,connectionsPerRoute,connectionTimeToLive,"
                              + "httpBinding,httpClientConfigurer,httpConfiguration,httpContext,httpRegistry,maxTotalConnections,connectionRequestTimeout,"
                              + "connectTimeout,socketTimeout,cookieStore,x509HostnameVerifier,sslContextParameters,useGlobalSslContextParameters,"
                              + "proxyMethod,proxyHost,proxyPort,proxyAuthScheme,proxyAuthMethod,proxyAuthUsername,proxyAuthPassword,proxyAuthHost,proxyAuthPort,proxyAuthDomain,proxyAuthNtHost")
public class ResteasyComponent extends HttpComponent implements RestConsumerFactory {

    @Metadata(label = "advanced")
    private HttpRegistry httpRegistry;
    @Metadata(label = "consumer")
    private String proxyConsumersClasses;

    public ResteasyComponent() {
        super();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // must extract well known parameters before we create the endpoint
        Boolean throwExceptionOnFailure = getAndRemoveParameter(parameters, "throwExceptionOnFailure", Boolean.class);
        Boolean transferException = getAndRemoveParameter(parameters, "transferException", Boolean.class);
        Boolean matchOnUriPrefix = getAndRemoveParameter(parameters, "matchOnUriPrefix", Boolean.class);
        String httpMethodRestrict = getAndRemoveParameter(parameters, "httpMethodRestrict", String.class);
        HeaderFilterStrategy headerFilterStrategy
                = resolveAndRemoveReferenceParameter(parameters, "headerFilterStrategy", HeaderFilterStrategy.class);

        // restructure uri to be based on the parameters left as we don't want to include the Camel internal options
        URI httpUri = URISupport.createRemainingURI(new URI(UnsafeUriCharactersEncoder.encodeHttpURI(uri)), parameters);

        ResteasyEndpoint endpoint = new ResteasyEndpoint(uri, this, httpUri);

        // Needed for taking component options from URI and using only clean uri for resource. Later adding query parameters
        setProperties(endpoint, parameters);

        if (matchOnUriPrefix != null) {
            endpoint.setMatchOnUriPrefix(matchOnUriPrefix);
        }
        //set parameters if they exists in URI
        if (httpMethodRestrict != null) {
            endpoint.setHttpMethodRestrict(httpMethodRestrict);
        }
        if (headerFilterStrategy != null) {
            endpoint.setHeaderFilterStrategy(headerFilterStrategy);
        }
        if (throwExceptionOnFailure != null) {
            endpoint.setThrowExceptionOnFailure(throwExceptionOnFailure);
        }
        if (transferException != null) {
            endpoint.setTransferException(transferException);
        }

        // construct URI so we can use it to get the splitted information
        URI u = new URI(remaining);
        String protocol = u.getScheme();

        String uriPattern = u.getPath();
        if (parameters.size() > 0) {
            uriPattern = uriPattern + "?" + URISupport.createQueryString(parameters);
        }

        int port = 0;
        String host = u.getHost();
        if (u.getPort() > 0) {
            port = u.getPort();
        }

        endpoint.setProtocol(protocol);
        endpoint.setUriPattern(uriPattern);
        endpoint.setHost(host);
        if (port > 0) {
            endpoint.setPort(port);
        }
        return endpoint;
    }

    @Override
    public void connect(HttpConsumer consumer) throws Exception {
        ResteasyConsumer sc = (ResteasyConsumer) consumer;
        String name = sc.getEndpoint().getServletName();
        HttpRegistry registry = httpRegistry;
        if (registry == null) {
            registry = DefaultHttpRegistry.getHttpRegistry(name);
        }
        registry.register(consumer);
    }

    @Override
    public void disconnect(HttpConsumer consumer) throws Exception {
        ResteasyConsumer sc = (ResteasyConsumer) consumer;
        String name = sc.getEndpoint().getServletName();
        HttpRegistry registry = httpRegistry;
        if (registry == null) {
            registry = DefaultHttpRegistry.getHttpRegistry(name);
        }
        registry.unregister(consumer);
    }

    public String getProxyConsumersClasses() {
        return proxyConsumersClasses;
    }

    /**
     * Proxy classes for consumer endpoints. Multiple classes can be separated by comma.
     */
    public void setProxyConsumersClasses(String proxyConsumersClasses) {
        this.proxyConsumersClasses = proxyConsumersClasses;
    }

    public HttpRegistry getHttpRegistry() {
        return httpRegistry;
    }

    /**
     * To use a custom HttpRegistry.
     */
    public void setHttpRegistry(HttpRegistry httpRegistry) {
        this.httpRegistry = httpRegistry;
    }

    @Override
    public Consumer createConsumer(
            CamelContext camelContext, Processor processor, String verb, String basePath,
            String uriTemplate, String consumes, String produces, RestConfiguration configuration,
            Map<String, Object> parameters)
            throws Exception {

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
        if (config.getComponent() == null || config.getComponent().equals("resteasy")) {
            // setup endpoint options
            if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
                map.putAll(config.getEndpointProperties());
            }
        }

        String query = URISupport.createQueryString(map);

        String url = "resteasy:/%s";
        if (!query.isEmpty()) {
            url = url + "?" + query;
        }

        // must use upper case for restrict
        String restrict = verb.toUpperCase(Locale.US);

        // get the endpoint
        url = String.format(url, path, restrict);

        ResteasyEndpoint endpoint = (ResteasyEndpoint) camelContext.getEndpoint(url, parameters);

        Consumer consumer = endpoint.createConsumer(processor);
        if (config.getConsumerProperties() != null && !config.getConsumerProperties().isEmpty()) {
            setProperties(consumer, config.getConsumerProperties());
        }
        return consumer;
    }

}
