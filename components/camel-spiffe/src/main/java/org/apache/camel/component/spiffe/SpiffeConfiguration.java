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

    @UriParam(defaultValue = "fetchX509Svid",
              description = "What the producer does on the SPIFFE Workload API: fetchX509Svid fetches the X.509-SVID of"
                            + " this workload, fetchJwtSvid fetches a JWT-SVID for the audience, and validateJwtSvid"
                            + " validates the JWT-SVID of the message against the audience.")
    private SpiffeOperation operation = SpiffeOperation.fetchX509Svid;

    @UriParam(label = "producer,security", defaultValue = "chain")
    private SpiffeX509Response x509Response = SpiffeX509Response.chain;

    @UriParam(label = "producer,security")
    private String localSpiffeId;

    @UriParam(label = "security")
    private String spiffeSocketPath;

    @UriParam
    private String audience;

    @UriParam(defaultValue = "false",
              description = "Whether validateJwtSvid keeps the message body. When false, the token can also come from the"
                            + " body, and the body is replaced with the validated JwtSvid. When true, the token must be in"
                            + " the CamelSpiffeToken header or an Authorization: Bearer header. In both cases the"
                            + " CamelSpiffeJwtIdentity exchange property holds the verified identity.")
    private boolean preserveBody;

    @UriParam(label = "security", defaultValue = "false", security = "insecure:dev")
    private boolean allowOperationHeader;

    @UriParam(label = "health", defaultValue = "60000", javaType = "java.time.Duration",
              description = "The minimum remaining validity, in milliseconds, of the X.509-SVID for the producer health"
                            + " check to report UP: at or below it, the check reports DOWN before the SVID expires. Zero only"
                            + " requires an unexpired SVID. Used only with fetchX509Svid, as for the JWT operations the"
                            + " check watches the JWT bundles, which have no expiry.")
    private long healthCheckExpiryThreshold = 60000;

    @UriParam(label = "advanced",
              description = "An existing WorkloadApiClient to use instead of a client that the endpoint creates. The"
                            + " endpoint does not close it, spiffeSocketPath is ignored, and the producer registers no"
                            + " health check.")
    @Metadata(autowired = true)
    private WorkloadApiClient workloadApiClient;

    public SpiffeOperation getOperation() {
        return operation;
    }

    public void setOperation(SpiffeOperation operation) {
        this.operation = operation;
    }

    public SpiffeX509Response getX509Response() {
        return x509Response;
    }

    /**
     * What the {@code fetchX509Svid} operation returns in the message body.
     * <p/>
     * Defaults to {@code chain}: the X.509 certificate chain without the private key, so a route never handles key
     * material unless it asks for it. Choose {@code svid} to get the whole {@code X509Svid} including the <em>private
     * key</em> (needed for programmatic mTLS), or {@code id} to leave the body untouched. The SPIFFE ID and expiry are
     * exposed through the {@code CamelSpiffeSpiffeId} and {@code CamelSpiffeExpiry} headers in every case.
     */
    public void setX509Response(SpiffeX509Response x509Response) {
        this.x509Response = x509Response;
    }

    public String getLocalSpiffeId() {
        return localSpiffeId;
    }

    /**
     * The exact local SPIFFE ID to use when the Workload API supplies several identities: fetchX509Svid selects that
     * X.509-SVID instead of the SDK's default one, and fetchJwtSvid mints the JWT-SVID with it as the subject. A
     * configured identity that is absent, or that the workload is not entitled to, fails without selecting a different
     * identity. This option comes only from endpoint configuration, never from a message header; validateJwtSvid has no
     * local identity to choose and ignores it.
     */
    public void setLocalSpiffeId(String localSpiffeId) {
        this.localSpiffeId = localSpiffeId;
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
     * (validateJwtSvid). fetchJwtSvid requests all of them and can be overridden per-message with the
     * {@code CamelSpiffeAudience} header; validateJwtSvid ignores that header and uses this configuration only,
     * accepting the token if it matches any of the configured audiences, trying each in turn.
     */
    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public boolean isPreserveBody() {
        return preserveBody;
    }

    public void setPreserveBody(boolean preserveBody) {
        this.preserveBody = preserveBody;
    }

    public boolean isAllowOperationHeader() {
        return allowOperationHeader;
    }

    /**
     * Whether the {@code CamelSpiffeOperation} header may override the configured operation.
     * <p/>
     * Disabled by default: the operation decides whether this endpoint <em>validates</em> a token or <em>mints</em>
     * one, so a message that can set it can turn a validator into an endpoint that hands out this workload's own
     * JWT-SVID. Enable it only on routes whose input is trusted.
     */
    public void setAllowOperationHeader(boolean allowOperationHeader) {
        this.allowOperationHeader = allowOperationHeader;
    }

    public long getHealthCheckExpiryThreshold() {
        return healthCheckExpiryThreshold;
    }

    public void setHealthCheckExpiryThreshold(long healthCheckExpiryThreshold) {
        this.healthCheckExpiryThreshold = healthCheckExpiryThreshold;
    }

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
