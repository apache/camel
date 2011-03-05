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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Endpoint;
import org.apache.camel.component.http.AuthMethod;
import org.apache.camel.component.http.HttpBinding;
import org.apache.camel.component.http.HttpClientConfigurer;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.component.http.HttpConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.params.HttpClientParams;

public class ServletComponent extends HttpComponent {

    private String servletName = "CamelServlet";
    
    private HttpRegistry httpRegistry;

    public String getServletName() {
        return servletName;
    }

    public void setServletName(String servletName) {
        this.servletName = servletName;
    }
    
    public void setHttpRegistry(HttpRegistry httpRegistry) {
        this.httpRegistry = httpRegistry;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        
        if (httpRegistry == null) {
            httpRegistry = DefaultHttpRegistry.getSingletonHttpRegistry();
        }

        uri = uri.startsWith("servlet:") ? remaining : uri;

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

        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        URI httpUri = URISupport.createRemainingURI(new URI(UnsafeUriCharactersEncoder.encode(uri)), CastUtils.cast(parameters));
        uri = httpUri.toString();

        ServletEndpoint endpoint = createServletEndpoint(uri, this, httpUri, params, getHttpConnectionManager(), configurer);
        endpoint.setServletName(servletName);
        setEndpointHeaderFilterStrategy(endpoint);

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

        setProperties(endpoint, parameters);
        return endpoint;
    }

    /**
     * Strategy to create the servlet endpoint.
     */
    protected ServletEndpoint createServletEndpoint(String endpointUri,
            ServletComponent component, URI httpUri, HttpClientParams params,
            HttpConnectionManager httpConnectionManager,
            HttpClientConfigurer clientConfigurer) throws Exception {
        return new ServletEndpoint(endpointUri, component, httpUri, params, httpConnectionManager, clientConfigurer);
    }

    @Override
    public void connect(HttpConsumer consumer) throws Exception {
        httpRegistry.register(consumer);
    }

    @Override
    public void disconnect(HttpConsumer consumer) throws Exception {
        httpRegistry.unregister(consumer);
    }
    
    
}