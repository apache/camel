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
import java.net.URISyntaxException;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.http.HttpClientConfigurer;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * Defines the Resteasy Endpoint.
 * It contains a list of properties for Resteasy endpoint including {@link org.apache.camel.component.resteasy.ResteasyHttpBinding},
 * and {@link HeaderFilterStrategy}.
 *
 */
@UriEndpoint(firstVersion = "3.4.0", scheme = "resteasy",  extendsScheme = "http",
        title = "Resteasy", syntax = "resteasy:contextPath", label = "rest")
public class ResteasyEndpoint extends HttpEndpoint implements HeaderFilterStrategyAware {
    @UriParam
    private  String resteasyMethod = "GET";

    @UriParam
    private ResteasyHttpBinding restEasyHttpBindingRef;

    @UriParam
    private String servletName;

    @UriParam
    private String proxyClientClass;

    @UriParam
    private String proxyMethod;

    @UriParam
    private Boolean setHttpResponseDuringProcessing = false;

    @UriParam
    private Boolean skipServletProcessing = false;

    @UriParam
    private Boolean basicAuth = false;

    @UriParam
    private String username;

    @UriParam
    private String password;

    private String protocol;
    private String host;
    private int port;
    private String uriPattern;

//    Using default camel headerFilterStrategy -> possibility to create your own strategy and set it on endpoint
    @UriParam
    private HeaderFilterStrategy headerFilterStrategy = new ResteasyHeaderFilterStrategy();

    @UriParam
    private boolean throwExceptionOnFailure = true;


    public ResteasyEndpoint(String endPointURI, ResteasyComponent component, URI httpUri, HttpClientConnectionManager httpConnectionManager,
                            HttpClientConfigurer clientConfigurer) throws URISyntaxException {
        super(endPointURI, component, httpUri,
                HttpClientBuilder.create(),
                httpConnectionManager, clientConfigurer);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new ResteasyProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ResteasyConsumer answer = new ResteasyConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }


    public String getProxyMethod() {
        return proxyMethod;
    }

    /**
     * Sets the proxy method defined in an interface
    */

    public void setProxyMethod(String proxyMethod) {
        this.proxyMethod = proxyMethod;
    }

    public Boolean getSetHttpResponseDuringProcessing() {
        return setHttpResponseDuringProcessing;
    }

    /**
     * Sets the flag to use the endpoint where you can either populate camel exchange
     * from servlet response or use request itself which may be thought as if it is a proxy.
    */
    public void setSetHttpResponseDuringProcessing(Boolean setHttpResponseDuringProcessing) {
        this.setHttpResponseDuringProcessing = setHttpResponseDuringProcessing;
    }

    public Boolean getSkipServletProcessing() {
        return skipServletProcessing;
    }

    /**
     * Sets the flag to use skip servlet processing and let camel take over processing
    */
    public void setSkipServletProcessing(Boolean skipServletProcessing) {
        this.skipServletProcessing = skipServletProcessing;
    }

    public String getProxyClientClass() {
        return proxyClientClass;
    }

    /**
     * Sets the resteasy proxyClientClass
    */
    public void setProxyClientClass(String proxyClientClass) {
        this.proxyClientClass = proxyClientClass;
    }

    public String getServletName() {
        return servletName;
    }

    /**
     * Sets the servlet name
    */
    public void setServletName(String servletName) {
        this.servletName = servletName;
    }

    public String getResteasyMethod() {
        return resteasyMethod;
    }

    /**
     * Sets the resteasy method to process the request
    */
    public void setResteasyMethod(String resteasyMethod) {
        this.resteasyMethod = resteasyMethod;
    }

    public String getProtocol() {
        return protocol;
    }

    /**
     * Sets the protocol
    */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    /**
     * Sets the host
    */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Sets the port
    */
    public void setPort(int port) {
        this.port = port;
    }

    public String getUriPattern() {
        return uriPattern;
    }

    /**
     * Sets the uriPattern
    */
    public void setUriPattern(String uriPattern) {
        this.uriPattern = uriPattern;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    /**
     * ets the flag to propogate the exception
    */
    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public Boolean getBasicAuth() {
        return basicAuth;
    }

    /**
     * Sets the flag to basicAuth on endpoint
    */
    public void setBasicAuth(Boolean basicAuth) {
        this.basicAuth = basicAuth;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Sets the username
    */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Sets the password
    */
    public void setPassword(String password) {
        this.password = password;
    }

    public ResteasyHttpBinding getRestEasyHttpBindingRef() {
        return restEasyHttpBindingRef;
    }

    /**
     * Sets the restEasyHttpBinding if you have a customised one registered in the context
    */
    public void setRestEasyHttpBindingRef(ResteasyHttpBinding restEasyHttpBinding) {
        this.restEasyHttpBindingRef = restEasyHttpBinding;
    }


}
