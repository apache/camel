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
