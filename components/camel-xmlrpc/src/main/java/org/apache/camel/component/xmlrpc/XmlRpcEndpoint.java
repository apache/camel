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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.SynchronousDelegateProducer;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

/**
 * Represents a xmlrpc endpoint.
 */
public class XmlRpcEndpoint extends DefaultEndpoint {
    private String address;
    private XmlRpcClientConfigurer clientConfigurer;
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

    public void setAddress(String address) {
        this.address = address;
    }

    public XmlRpcClientConfigurer getClientConfigurer() {
        return clientConfigurer;
    }

    public void setClientConfigurer(XmlRpcClientConfigurer configurer) {
        this.clientConfigurer = configurer;
    }
    
    public void setClientConfig(XmlRpcClientConfigImpl config) {
        this.clientConfig = config;
    }
    
    public XmlRpcClientConfigImpl getClientConfig() {
        return clientConfig;
    }
    
    
    
    
}
