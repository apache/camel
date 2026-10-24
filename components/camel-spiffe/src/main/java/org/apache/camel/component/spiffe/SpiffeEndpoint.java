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
package org.apache.camel.component.spiffe;

import io.spiffe.workloadapi.DefaultWorkloadApiClient;
import io.spiffe.workloadapi.WorkloadApiClient;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Fetch and validate SPIFFE workload identity (X.509-SVID and JWT-SVID) from the SPIFFE Workload API.
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "spiffe", title = "SPIFFE",
             syntax = "spiffe:label", producerOnly = true, category = { Category.SECURITY },
             headersClass = SpiffeConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Only validateJwtSvid reads the body: the token as text, when neither the"
                                                + " CamelSpiffeToken header nor an Authorization: Bearer header carries it."
                                                + " The fetch operations ignore the body"),
           producerOut = @BodyType(value = Carrier.LIST, javaType = "java.util.List<java.security.cert.X509Certificate>",
                                   dependsOn = "allowOperationHeader",
                                   byOption = {
                                           @BodyType.When(option = "x509Response", value = "svid", carriers = Carrier.POJO,
                                                          javaType = "io.spiffe.svid.x509svid.X509Svid"),
                                           @BodyType.When(option = "x509Response", value = "id",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "fetchJwtSvid",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "validateJwtSvid",
                                                          carriers = Carrier.POJO,
                                                          javaType = "io.spiffe.svid.jwtsvid.JwtSvid") },
                                   description = "fetchX509Svid: the certificate chain without the private key; the X509Svid"
                                                 + " with its private key with x509Response=svid, or the body kept with"
                                                 + " x509Response=id. fetchJwtSvid: the token. validateJwtSvid: the validated"
                                                 + " JwtSvid. The SPIFFE ID is in the CamelSpiffeSpiffeId header"))
public class SpiffeEndpoint extends DefaultEndpoint {

    @UriPath(description = "Logical name of the endpoint")
    @Metadata(required = false)
    private String label;

    @UriParam
    private SpiffeConfiguration configuration;

    private WorkloadApiClient workloadApiClient;
    private boolean ownClient;

    public SpiffeEndpoint(final String uri, final Component component, final SpiffeConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (configuration.getWorkloadApiClient() != null) {
            workloadApiClient = configuration.getWorkloadApiClient();
            ownClient = false;
        } else if (ObjectHelper.isNotEmpty(configuration.getSpiffeSocketPath())) {
            workloadApiClient = DefaultWorkloadApiClient.newClient(
                    DefaultWorkloadApiClient.ClientOptions.builder()
                            .spiffeSocketPath(configuration.getSpiffeSocketPath())
                            .build());
            ownClient = true;
        } else {
            // uses the SPIFFE_ENDPOINT_SOCKET environment variable
            workloadApiClient = DefaultWorkloadApiClient.newClient();
            ownClient = true;
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (ownClient && workloadApiClient != null) {
            workloadApiClient.close();
        }
        workloadApiClient = null;
        super.doStop();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SpiffeProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * The endpoint configuration.
     */
    public SpiffeConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(SpiffeConfiguration configuration) {
        this.configuration = configuration;
    }

    public WorkloadApiClient getWorkloadApiClient() {
        return workloadApiClient;
    }
}
