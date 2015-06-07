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
package org.apache.camel.component.gae.login;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.gae.bind.OutboundBinding;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * Represents a <a href="http://camel.apache.org/glogin.html">GLogin
 * Endpoint</a>.
 */
@UriEndpoint(scheme = "glogin", title = "Google Login", syntax = "glogin:hostName", producerOnly = true, label = "cloud")
public class GLoginEndpoint extends DefaultEndpoint {

    private OutboundBinding<GLoginEndpoint, GLoginData, GLoginData> outboundBinding;

    @UriPath @Metadata(required = "true")
    private String hostName;
    @UriParam
    private String clientName;
    @UriParam
    private String userName;
    @UriParam
    private String password;
    @UriParam
    private int devPort;
    @UriParam
    private boolean devAdmin;
    @UriParam
    private boolean devMode;
    private GLoginService service;

    /**
     * Creates a new GLoginEndpoint.
     * 
     * @param endpointUri the endpoint uri
     * @param component component that created this endpoint.
     * @param hostName internet hostname of a GAE application, for example
     *            <code>example.appspot.com</code>, or <code>localhost</code> if
     *            the application is running on a local development server.
     * @param devPort port for connecting to the local development server.
     */
    public GLoginEndpoint(String endpointUri, Component component, String hostName, int devPort) {
        super(endpointUri, component);
        this.hostName = hostName;
        this.clientName = "apache-camel-2.x";
        this.devPort = devPort;
        this.devAdmin = false;
    }

    /**
     * Returns the component instance that created this endpoint.
     */
    @Override
    public GLoginComponent getComponent() {
        return (GLoginComponent)super.getComponent();
    }

    /**
     * Returns the internet hostname of the GAE application where to login.
     */
    public String getHostName() {
        return hostName;
    }

    public OutboundBinding<GLoginEndpoint, GLoginData, GLoginData> getOutboundBinding() {
        return outboundBinding;
    }

    /**
     * Sets the outbound binding for <code>glogin</code> endpoints. Default binding
     * is {@link GLoginBinding}.
     */
    public void setOutboundBinding(OutboundBinding<GLoginEndpoint, GLoginData, GLoginData> outboundBinding) {
        this.outboundBinding = outboundBinding;
    }

    public String getClientName() {
        return clientName;
    }

    /**
     * Sets the client name used for authentication. The default name is
     * <code>apache-camel-2.x</code>.
     */
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getUserName() {
        return userName;
    }

    /**
     * Sets the login username (a Google mail address).
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Sets the login password.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Returns the port for connecting to a development server. Only used
     * if {@link #devMode} is <code>true</code>. Default is 8080.
     */
    public int getDevPort() {
        return devPort;
    }

    public boolean isDevAdmin() {
        return devAdmin;
    }

    /**
     * Set to <code>true</code> for logging in as admin to a development server.
     * Only used if {@link #devMode} is <code>true</code>. Default is
     * <code>false</code>.
     */
    public void setDevAdmin(boolean devAdmin) {
        this.devAdmin = devAdmin;
    }

    public boolean isDevMode() {
        return devMode;
    }

    /**
     * Set to <code>true</code> for connecting to a development server.
     */
    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }

    public GLoginService getService() {
        return service;
    }

    /**
     * Sets the service that makes the remote calls to Google services or the
     * local development server. Testing code should inject a mock service here
     * (using serviceRef in endpoint URI).
     */
    public void setService(GLoginService service) {
        this.service = service;
    }

    /**
     * Creates a {@link GLoginProducer}.
     */
    public Producer createProducer() throws Exception {
        return new GLoginProducer(this);
    }

    /**
     * throws {@link UnsupportedOperationException}
     */
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("consumption from glogin endpoint not supported");
    }

    /**
     * Returns <code>true</code>.
     */
    public boolean isSingleton() {
        return true;
    }

}
