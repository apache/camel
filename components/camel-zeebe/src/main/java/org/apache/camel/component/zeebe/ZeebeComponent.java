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

package org.apache.camel.component.zeebe;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.zeebe.internal.OperationName;
import org.apache.camel.component.zeebe.internal.ZeebeService;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultComponent;

@org.apache.camel.spi.annotations.Component("zeebe")
public class ZeebeComponent extends DefaultComponent {

    @Metadata(defaultValue = "" + ZeebeConstants.DEFAULT_GATEWAY_HOST, label = "security")
    String gatewayHost = ZeebeConstants.DEFAULT_GATEWAY_HOST;
    @Metadata(defaultValue = "" + ZeebeConstants.DEFAULT_GATEWAY_PORT, label = "security")
    int gatewayPort = ZeebeConstants.DEFAULT_GATEWAY_PORT;
    @Metadata(label = "security", secret = true)
    String clientId;
    @Metadata(label = "security", secret = true)
    String clientSecret;
    @Metadata
    String oAuthAPI;

    private ZeebeService zeebeService;

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        OperationName operationName = null;

        operationName = OperationName.fromValue(remaining);

        Endpoint endpoint = new ZeebeEndpoint(uri, this, operationName);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public ZeebeService getZeebeService() {
        return zeebeService;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * Client id to be used when requesting access token from OAuth authorization server.
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * Client secret to be used when requesting access token from OAuth authorization server.
     */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getOAuthAPI() {
        return oAuthAPI;
    }

    /**
     * The authorization server's URL, from which the access token will be requested.
     */
    public void setOAuthAPI(String oAuthAPI) {
        this.oAuthAPI = oAuthAPI;
    }

    public String getGatewayHost() {
        return gatewayHost;
    }

    /**
     * The gateway server hostname to connect to the Zeebe cluster.
     */
    public void setGatewayHost(String gatewayHost) {
        this.gatewayHost = gatewayHost;
    }

    public int getGatewayPort() {
        return gatewayPort;
    }

    /**
     * The gateway server port to connect to the Zeebe cluster.
     */
    public void setGatewayPort(int gatewayPort) {
        this.gatewayPort = gatewayPort;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (zeebeService == null) {
            zeebeService = new ZeebeService(gatewayHost, gatewayPort);
            zeebeService.doStart();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (zeebeService != null) {
            zeebeService.doStop();
            zeebeService = null;
        }
    }
}
