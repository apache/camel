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
package org.apache.camel.component.corda;

import java.net.URI;
import java.net.URISyntaxException;
import net.corda.client.rpc.CordaRPCClient;
import net.corda.client.rpc.CordaRPCConnection;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.utilities.NetworkHostAndPort;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * The corda component uses the corda-rpc to interact with corda nodes.
 */
@UriEndpoint(firstVersion = "2.23.0", scheme = "corda", title = "corda", syntax = "corda:url", label = "corda,blockchain")
public class CordaEndpoint extends DefaultEndpoint {

    @UriPath(description = "URL to the corda node")
    @Metadata(required = true)
    private CordaConfiguration configuration;
    private CordaRPCConnection rpcConnection;
    private CordaRPCOps proxy;

    public CordaEndpoint(String uri, String remaining, CordaComponent component, CordaConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;

        try {
            URI nodeURI = new URI(uri);
            configuration.setHost(nodeURI.getHost());
            configuration.setPort(nodeURI.getPort());

            if (nodeURI.getUserInfo() != null) {
                String[] creds = nodeURI.getUserInfo().split(":");
                if (configuration.getUsername() == null) {
                    configuration.setUsername(creds[0]);
                }
                if (configuration.getPassword() == null) {
                    configuration.setPassword(creds.length > 1 ? creds[1] : "");
                }
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + remaining, e);
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        return new CordaProducer(this, configuration, proxy);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        CordaConsumer consumer = new CordaConsumer(this, processor, configuration, proxy);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected void doStart() throws Exception {
        NetworkHostAndPort rpcAddress = new NetworkHostAndPort(configuration.getHost(), configuration.getPort());
        CordaRPCClient rpcClient = new CordaRPCClient(rpcAddress);
        rpcConnection = rpcClient.start(this.configuration.getUsername(), this.configuration.getPassword());
        proxy = rpcConnection.getProxy();
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (rpcConnection != null) {
            rpcConnection.notifyServerAndClose();
        }
        super.doStop();
    }

    public boolean isSingleton() {
        return true;
    }

}
