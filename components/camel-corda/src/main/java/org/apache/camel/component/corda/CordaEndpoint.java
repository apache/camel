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
package org.apache.camel.component.corda;

import net.corda.client.rpc.CordaRPCClient;
import net.corda.client.rpc.CordaRPCConnection;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.utilities.NetworkHostAndPort;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * The corda component uses corda-rpc to interact with corda nodes.
 */
@UriEndpoint(firstVersion = "2.23.0", scheme = "corda", title = "Corda", syntax = "corda:node", label = "corda,blockchain")
public class CordaEndpoint extends DefaultEndpoint {

    @UriParam
    private CordaConfiguration configuration;

    private CordaRPCConnection rpcConnection;
    private CordaRPCOps proxy;

    public CordaEndpoint(String uri, CordaComponent component, CordaConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
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

    public CordaConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(CordaConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        NetworkHostAndPort rpcAddress = new NetworkHostAndPort(configuration.retrieveHost(), configuration.retrievePort());
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

}
