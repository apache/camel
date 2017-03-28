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
package org.apache.camel.component.xmlrpc;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.SynchronousDelegateProducer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

/**
 * The xmlrpc component is used for sending messages to a XML RPC service.
 */
@UriEndpoint(firstVersion = "2.11.0", scheme = "xmlrpc", title = "XML RPC", syntax = "xmlrpc:address", producerOnly = true, label = "transformation")
public class XmlRpcEndpoint extends DefaultEndpoint {
    @UriPath @Metadata(required = "true")
    private String address;
    @UriParam
    private XmlRpcConfiguration configuration;
    @UriParam
    private String defaultMethodName;
    @UriParam(label = "advanced")
    private XmlRpcClientConfigurer clientConfigurer;
    @UriParam(label = "advanced")
    private XmlRpcClientConfigImpl clientConfig = new XmlRpcClientConfigImpl();

    public XmlRpcEndpoint() {
    }

    public XmlRpcEndpoint(String uri, XmlRpcComponent component, String address) {
        super(uri, component);
        this.setAddress(address);
    }

    public Producer createProducer() throws Exception {
        Producer answer = new XmlRpcProducer(this);
        if (isSynchronous()) {
            return new SynchronousDelegateProducer(answer);
        } else {
            return answer;
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("This component does not support consuming from this endpoint");
    }

    public boolean isSingleton() {
        return true;
    }

    public XmlRpcClient createClient() throws MalformedURLException {
        XmlRpcClient client = new XmlRpcClient();
        // setup the client with the configuration from the XmlRpcEndpoint
        XmlRpcClientConfigImpl config = clientConfig.cloneMe();
        // setup the server url
        config.setServerURL(new URL(getAddress()));
        client.setConfig(config);
        if (clientConfigurer != null) {
            clientConfigurer.configureXmlRpcClient(client);
        }
        return client;
    }

    public String getAddress() {
        return address;
    }

    /**
     * The server url
     */
    public void setAddress(String address) {
        this.address = address;
    }

    public XmlRpcClientConfigurer getClientConfigurer() {
        return clientConfigurer;
    }

    /**
     * To use a custom XmlRpcClientConfigurer to configure the client
     */
    public void setClientConfigurer(XmlRpcClientConfigurer configurer) {
        this.clientConfigurer = configurer;
    }

    /**
     * To use the given XmlRpcClientConfigImpl as configuration for the client.
     */
    public void setClientConfig(XmlRpcClientConfigImpl config) {
        this.clientConfig = config;
    }
    
    public XmlRpcClientConfigImpl getClientConfig() {
        return clientConfig;
    }

    public String getDefaultMethodName() {
        return defaultMethodName;
    }

    /**
     * The method name which would be used for the xmlrpc requests by default, if the Message header CamelXmlRpcMethodName is not set.
     */
    public void setDefaultMethodName(String defaultMethodName) {
        this.defaultMethodName = defaultMethodName;
    }

    public XmlRpcConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(XmlRpcConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (clientConfig == null) {
            clientConfig = new XmlRpcClientConfigImpl();
        }

        Map<String, Object> params = new HashMap<String, Object>();
        // do not include null values
        IntrospectionSupport.getProperties(configuration, params, null, false);
        setProperties(clientConfig, params);
    }
}
