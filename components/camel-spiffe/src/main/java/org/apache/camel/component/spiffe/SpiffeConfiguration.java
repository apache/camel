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

import io.spiffe.workloadapi.WorkloadApiClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class SpiffeConfiguration implements Cloneable {

    @UriParam(defaultValue = "fetchX509Svid")
    private SpiffeOperation operation = SpiffeOperation.fetchX509Svid;

    @UriParam(label = "security")
    private String spiffeSocketPath;

    @UriParam
    private String audience;

    @UriParam(label = "advanced",
              description = "An existing WorkloadApiClient to use. When set, the component does not"
                            + " create or close its own client and spiffeSocketPath is ignored.")
    @Metadata(autowired = true)
    private WorkloadApiClient workloadApiClient;

    /**
     * The operation to perform on the SPIFFE Workload API.
     */
    public SpiffeOperation getOperation() {
        return operation;
    }

    public void setOperation(SpiffeOperation operation) {
        this.operation = operation;
    }

    /**
     * The address of the SPIFFE Workload API endpoint (for example {@code unix:///tmp/agent.sock} or
     * {@code tcp://127.0.0.1:8082}). When not set, the {@code SPIFFE_ENDPOINT_SOCKET} environment variable is used.
     */
    public String getSpiffeSocketPath() {
        return spiffeSocketPath;
    }

    public void setSpiffeSocketPath(String spiffeSocketPath) {
        this.spiffeSocketPath = spiffeSocketPath;
    }

    /**
     * The comma-separated audience(s) to request for a JWT-SVID (fetchJwtSvid) or to validate against
     * (validateJwtSvid). Can be overridden per-message with the {@code CamelSpiffeAudience} header. Note that
     * validateJwtSvid validates against a single audience, so when several comma-separated audiences are given only the
     * first one is used for validation; fetchJwtSvid requests all of them.
     */
    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    /**
     * An already-configured {@link WorkloadApiClient} to use instead of letting the endpoint create one.
     */
    public WorkloadApiClient getWorkloadApiClient() {
        return workloadApiClient;
    }

    public void setWorkloadApiClient(WorkloadApiClient workloadApiClient) {
        this.workloadApiClient = workloadApiClient;
    }

    public SpiffeConfiguration copy() {
        try {
            return (SpiffeConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
