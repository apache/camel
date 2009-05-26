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
package org.apache.camel.component.http;

import java.net.URI;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.impl.HeaderFilterStrategyComponent;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.URISupport;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpClientParams;

/**
 * Defines the <a href="http://camel.apache.org/http.html">HTTP
 * Component</a>
 *
 * @version $Revision$
 */
public class HttpComponent extends HeaderFilterStrategyComponent {
    protected HttpClientConfigurer httpClientConfigurer;
    protected HttpConnectionManager httpConnectionManager = new MultiThreadedHttpConnectionManager();
    protected HttpBinding httpBinding;
    private boolean matchOnUriPrefix;
    
    /**
     * Connects the URL specified on the endpoint to the specified processor.
     *
     * @param  consumer the consumer
     * @throws Exception can be thrown
     */
    public void connect(HttpConsumer consumer) throws Exception {
    }

    /**
     * Disconnects the URL specified on the endpoint from the specified processor.
     *
     * @param  consumer the consumer
     * @throws Exception can be thrown
     */
    public void disconnect(HttpConsumer consumer) throws Exception {
    }

    /** 
     * Setting http binding and http client configurer according to the parameters
     * Also setting the BasicAuthenticationHttpClientConfigurer if the username 
     * and password option are not null.
     * 
     * @param parameters the map of parameters 
     */
    protected void configureParameters(Map parameters) {
        // lookup http binding in registry if provided
        String ref = getAndRemoveParameter(parameters, "httpBindingRef", String.class);
        if (ref != null) {
            httpBinding = CamelContextHelper.mandatoryLookup(getCamelContext(), ref, HttpBinding.class);
        }
        
        // lookup http client front configurer in the registry if provided
        ref = getAndRemoveParameter(parameters, "httpClientConfigurerRef", String.class);
        if (ref != null) {
            httpClientConfigurer = CamelContextHelper.mandatoryLookup(getCamelContext(), ref, HttpClientConfigurer.class);
        }
        
        // check the user name and password for basic authentication
        String username = getAndRemoveParameter(parameters, "username", String.class);
        String password = getAndRemoveParameter(parameters, "password", String.class);
        if (username != null && password != null) {
            
            httpClientConfigurer = CompositeHttpConfigurer.combineConfigurers(
                httpClientConfigurer, 
                new BasicAuthenticationHttpClientConfigurer(username, password));
        }
        
        // check the proxy details for proxy configuration
        String host = getAndRemoveParameter(parameters, "proxyHost", String.class);
        Integer port = getAndRemoveParameter(parameters, "proxyPort", Integer.class);
        if (host != null && port != null) {
        	String proxyUsername = getAndRemoveParameter(parameters, "proxyUsername", String.class);
            String proxyPassword = getAndRemoveParameter(parameters, "proxyPassword", String.class);
        	if(proxyUsername!=null && proxyPassword!=null){
        		httpClientConfigurer = CompositeHttpConfigurer.combineConfigurers(
        		    httpClientConfigurer,
        		    new ProxyHttpClientConfigurer(host, port, proxyUsername, proxyPassword));
        	} else {
        		httpClientConfigurer = CompositeHttpConfigurer.combineConfigurers(
        		    httpClientConfigurer,
        		    new ProxyHttpClientConfigurer(host, port));
        	}
        }
        matchOnUriPrefix = Boolean.parseBoolean(getAndRemoveParameter(parameters, "matchOnUriPrefix", String.class));
    }
    
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map parameters)
        throws Exception {

        // http client can be configured from URI options
        HttpClientParams clientParams = new HttpClientParams();
        IntrospectionSupport.setProperties(clientParams, parameters, "httpClient.");
        // validate that we could resolve all httpClient. parameters as this component is lenient
        validateParameters(uri, parameters, "httpClient.");

        configureParameters(parameters);

        // should we use an exception for failed error codes?
        Boolean throwExceptionOnFailure = getAndRemoveParameter(parameters, "throwExceptionOnFailure", Boolean.class);

        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        URI httpUri = URISupport.createRemainingURI(new URI(uri), parameters);
        uri = httpUri.toString();

        // validate http uri that end-user did not duplicate the http part that can be a common error
        String part = httpUri.getSchemeSpecificPart();
        if (part != null) {
            part = part.toLowerCase();
            if (part.startsWith("//http//") || part.startsWith("//https//")) {
                throw new ResolveEndpointFailedException(uri,
                        "The uri part is not configured correctly. You have duplicated the http(s) protocol.");
            }
        }

        HttpEndpoint endpoint = new HttpEndpoint(uri, this, httpUri, clientParams, httpConnectionManager, httpClientConfigurer);
        if (httpBinding != null) {
            endpoint.setBinding(httpBinding);
        }
        setEndpointHeaderFilterStrategy(endpoint);
        if (throwExceptionOnFailure != null) {
            endpoint.setThrowExceptionOnFailure(throwExceptionOnFailure);
        }
        setProperties(endpoint, parameters);
        return endpoint;
    }    
   
    @Override
    protected boolean useIntrospectionOnEndpoint() {
        return false;
    }

    public HttpClientConfigurer getHttpClientConfigurer() {
        return httpClientConfigurer;
    }

    public void setHttpClientConfigurer(HttpClientConfigurer httpClientConfigurer) {
        this.httpClientConfigurer = httpClientConfigurer;
    }

    public HttpConnectionManager getHttpConnectionManager() {
        return httpConnectionManager;
    }

    public void setHttpConnectionManager(HttpConnectionManager httpConnectionManager) {
        this.httpConnectionManager = httpConnectionManager;
    }

    public HttpBinding getHttpBinding() {
        return httpBinding;
    }

    public void setHttpBinding(HttpBinding httpBinding) {
        this.httpBinding = httpBinding;
    }

    public boolean isMatchOnUriPrefix() {
        return matchOnUriPrefix;
    }  
    
}
