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

import java.util.Set;

import io.spiffe.spiffeid.SpiffeId;
import io.spiffe.workloadapi.WorkloadApiClient;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;

/**
 * Validates an incoming JWT-SVID for configured audiences and authorizes its SPIFFE identity before a route segment.
 * The application body is never read as a token or replaced. The verified, token-free identity and claims are exposed
 * as the {@link SpiffeConstants#JWT_IDENTITY} exchange property.
 */
@Metadata(label = "bean",
          description = "Validates a JWT-SVID and authorizes its SPIFFE identity before a route segment, preserving the"
                        + " application body. A missing, rejected or unauthorized token fails closed.",
          annotations = { "interfaceName=org.apache.camel.spi.AuthorizationPolicy" })
@Configurer(metadataOnly = true)
public class SpiffeJwtSecurityPolicy implements AuthorizationPolicy {
    @Metadata(required = true,
              description = "Comma-separated audiences to validate against. A token is accepted when it validates for"
                            + " any configured audience. Message headers cannot override this check.")
    private String audience;
    @Metadata(description = "Comma-separated allow-list of validated SPIFFE identities to authorize. Set exactly one"
                            + " of acceptedSpiffeIds and acceptAnySpiffeId.")
    private String acceptedSpiffeIds;
    @Metadata(defaultValue = "false",
              description = "Authorize any SPIFFE identity whose JWT-SVID validates for a configured audience, leaving"
                            + " further authorization to a downstream policy. Mutually exclusive with acceptedSpiffeIds.")
    private boolean acceptAnySpiffeId;
    @Metadata(label = "security",
              description = "Path to the Workload API endpoint. Defaults to the spiffeSocketPath of the spiffe component,"
                            + " then to the SPIFFE_ENDPOINT_SOCKET environment variable. Ignored when a"
                            + " workloadApiClient is used.")
    private String spiffeSocketPath;
    @Metadata(label = "advanced",
              description = "An existing WorkloadApiClient to use. Defaults to the client of the spiffe component, when"
                            + " it has one. The client remains owned by its supplier and is not closed when the guarded"
                            + " route stops.")
    private WorkloadApiClient workloadApiClient;

    private volatile Set<SpiffeId> acceptedSet = Set.of();

    @Override
    public void beforeWrap(Route route, NamedNode definition) {
        SpiffeJwtValidator.audiences(audience, "set the audience option");
        boolean haveAllowList = ObjectHelper.isNotEmpty(acceptedSpiffeIds);
        if (acceptAnySpiffeId == haveAllowList) {
            throw new IllegalStateException(
                    "SpiffeJwtSecurityPolicy requires exactly one of acceptedSpiffeIds and acceptAnySpiffeId");
        }
        Set<SpiffeId> parsed = haveAllowList ? SpiffeSecurityPolicy.parseSpiffeIds(acceptedSpiffeIds) : Set.of();
        if (!acceptAnySpiffeId && parsed.isEmpty()) {
            throw new IllegalStateException("acceptedSpiffeIds did not contain any SPIFFE ID after trimming");
        }
        acceptedSet = parsed;
    }

    @Override
    public Processor wrap(Route route, Processor processor) {
        // Start from the configuration of the spiffe component, so that its camel.component.spiffe.* settings and a
        // client autowired on it apply to the policy as well; what is set on the policy itself takes precedence
        SpiffeComponent component = route.getCamelContext().getComponent("spiffe", SpiffeComponent.class);
        SpiffeConfiguration configuration = component.getConfiguration() != null
                ? component.getConfiguration().copy() : new SpiffeConfiguration();
        configuration.setOperation(SpiffeOperation.validateJwtSvid);
        configuration.setAudience(audience);
        configuration.setPreserveBody(true);
        if (spiffeSocketPath != null) {
            configuration.setSpiffeSocketPath(spiffeSocketPath);
        }
        if (workloadApiClient != null) {
            configuration.setWorkloadApiClient(workloadApiClient);
        }
        SpiffeEndpoint endpoint = new SpiffeEndpoint("spiffe:jwt-policy", component, configuration);
        return new SpiffeJwtSecurityProcessor(processor, this, endpoint);
    }

    boolean isAuthorized(SpiffeId id) {
        return acceptAnySpiffeId || acceptedSet.contains(id);
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getAcceptedSpiffeIds() {
        return acceptedSpiffeIds;
    }

    public void setAcceptedSpiffeIds(String acceptedSpiffeIds) {
        this.acceptedSpiffeIds = acceptedSpiffeIds;
    }

    public boolean isAcceptAnySpiffeId() {
        return acceptAnySpiffeId;
    }

    public void setAcceptAnySpiffeId(boolean acceptAnySpiffeId) {
        this.acceptAnySpiffeId = acceptAnySpiffeId;
    }

    public String getSpiffeSocketPath() {
        return spiffeSocketPath;
    }

    public void setSpiffeSocketPath(String spiffeSocketPath) {
        this.spiffeSocketPath = spiffeSocketPath;
    }

    public WorkloadApiClient getWorkloadApiClient() {
        return workloadApiClient;
    }

    public void setWorkloadApiClient(WorkloadApiClient workloadApiClient) {
        this.workloadApiClient = workloadApiClient;
    }
}
