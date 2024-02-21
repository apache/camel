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
package org.apache.camel.coap;

import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.tcp.netty.TcpClientConnector;

/**
 * Test the CoAP Rest Component with plain TCP.
 */
public class CoAPRestComponentTCPTest extends CoAPRestComponentTestBase {

    @Override
    protected String getProtocol() {
        return "coap+tcp";
    }

    @Override
    protected void decorateClient(CoapClient client) {
        Configuration config = Configuration.createStandardWithoutFile();
        TcpClientConnector tcpConnector = new TcpClientConnector(config);
        CoapEndpoint.Builder tcpBuilder = new CoapEndpoint.Builder();
        tcpBuilder.setConnector(tcpConnector);

        client.setEndpoint(tcpBuilder.build());
    }

    @Override
    protected void decorateRestConfiguration(RestConfigurationDefinition restConfig) {
        // Nothing here
    }

}
