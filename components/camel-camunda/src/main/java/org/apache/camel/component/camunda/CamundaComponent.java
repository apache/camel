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
package org.apache.camel.component.camunda;

import java.net.URI;
import java.util.Map;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import org.apache.camel.Endpoint;
import org.apache.camel.component.camunda.internal.CamundaService;
import org.apache.camel.component.camunda.internal.OperationName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultComponent;

@org.apache.camel.spi.annotations.Component("camunda")
public class CamundaComponent extends DefaultComponent {

    @Metadata(label = "security", description = "Camunda SaaS cluster ID. When set, the client connects via the cloud builder.")
    String clusterId;

    @Metadata(label = "security", description = "Camunda SaaS region (default: bru-2).", defaultValue = "bru-2")
    String region = "bru-2";

    @Metadata(label = "security", security = "secret",
              description = "Client ID for OAuth / SaaS authentication.")
    String clientId;

    @Metadata(label = "security", security = "secret",
              description = "Client secret for OAuth / SaaS authentication.")
    String clientSecret;

    @Metadata(label = "common",
              description = "gRPC address of the Camunda cluster (e.g. http://localhost:26500). "
                            + "Used for self-managed connections when clusterId is not set.",
              defaultValue = "http://localhost:26500")
    String grpcAddress = "http://localhost:26500";

    @Metadata(label = "common",
              description = "REST address of the Camunda cluster (e.g. http://localhost:8080). "
                            + "Used for self-managed connections when clusterId is not set.",
              defaultValue = "http://localhost:8080")
    String restAddress = "http://localhost:8080";

    @Metadata(label = "security",
              description = "OAuth authorization server URL for self-managed authentication.")
    String oAuthAPI;

    private CamundaService camundaService;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        OperationName operationName = OperationName.fromValue(remaining);

        Endpoint endpoint = new CamundaEndpoint(uri, this, operationName);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public CamundaService getCamundaService() {
        return camundaService;
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getGrpcAddress() {
        return grpcAddress;
    }

    public void setGrpcAddress(String grpcAddress) {
        this.grpcAddress = grpcAddress;
    }

    public String getRestAddress() {
        return restAddress;
    }

    public void setRestAddress(String restAddress) {
        this.restAddress = restAddress;
    }

    public String getOAuthAPI() {
        return oAuthAPI;
    }

    public void setOAuthAPI(String oAuthAPI) {
        this.oAuthAPI = oAuthAPI;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (camundaService == null) {
            CamundaClient client = buildClient();
            camundaService = new CamundaService(client);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (camundaService != null) {
            camundaService.doStop();
            camundaService = null;
        }
    }

    private CamundaClient buildClient() {
        if (clusterId != null) {
            return CamundaClient.newCloudClientBuilder()
                    .withClusterId(clusterId)
                    .withClientId(clientId)
                    .withClientSecret(clientSecret)
                    .withRegion(region)
                    .build();
        }

        CamundaClientBuilder builder = CamundaClient.newClientBuilder()
                .grpcAddress(URI.create(grpcAddress))
                .restAddress(URI.create(restAddress));

        if (clientId != null && oAuthAPI != null) {
            builder.credentialsProvider(
                    new io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder()
                            .authorizationServerUrl(oAuthAPI)
                            .audience(grpcAddress)
                            .clientId(clientId)
                            .clientSecret(clientSecret)
                            .build());
        }

        return builder.build();
    }
}
