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

import java.util.LinkedHashSet;
import java.util.Set;

import io.spiffe.spiffeid.SpiffeId;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link AuthorizationPolicy} that authorizes a route segment on the caller's verified SPIFFE ID.
 * <p>
 * Wrap a part of a route with it to make a routing decision on the peer identity that SPIFFE mutual TLS established,
 * without writing the check into the route by hand:
 *
 * <pre>
 * from("netty-http:https://0.0.0.0:8443?sslContextParameters=#spiffeSsl")
 *         .policy(spiffePolicy) // acceptedSpiffeIds = spiffe://example.org/frontend
 *         .to("direct:handleOrder");
 * </pre>
 *
 * The peer SPIFFE ID is read from the <em>verified</em> TLS peer certificate the consumer put on the exchange, never
 * from a message header (see CAMEL-24730). A peer that is not on the {@code acceptedSpiffeIds} allow-list - or presents
 * no verified SVID at all - is denied with a {@link org.apache.camel.CamelAuthorizationException}, matching the sibling
 * {@code ShiroSecurityPolicy} / {@code KeycloakSecurityPolicy} / {@code OpaSecurityPolicy}.
 * <p>
 * The policy answers "is the peer who they claim to be"; it is designed to compose with {@code camel-opa} ("may they do
 * this") rather than duplicate it. On a successful authorization the verified SPIFFE ID is stored as the
 * {@link SpiffeConstants#PEER_SPIFFE_ID} exchange property, so an OPA policy downstream can forward it to a Rego rule
 * through its {@code includeProperties} option (CAMEL-24643).
 */
public class SpiffeSecurityPolicy implements AuthorizationPolicy {

    /**
     * Default header carrying the verified {@link javax.net.ssl.SSLSession}; the one camel-netty-http populates.
     */
    public static final String DEFAULT_SSL_SESSION_HEADER = "CamelNettySSLSession";

    private static final Logger LOG = LoggerFactory.getLogger(SpiffeSecurityPolicy.class);

    private String acceptedSpiffeIds;
    private boolean acceptAnySpiffeId;
    private String sslSessionHeader = DEFAULT_SSL_SESSION_HEADER;

    private volatile Set<SpiffeId> acceptedSet = Set.of();

    public SpiffeSecurityPolicy() {
    }

    public SpiffeSecurityPolicy(String acceptedSpiffeIds) {
        this.acceptedSpiffeIds = acceptedSpiffeIds;
    }

    @Override
    public void beforeWrap(Route route, NamedNode definition) {
        boolean haveAllowList = ObjectHelper.isNotEmpty(acceptedSpiffeIds);
        if (acceptAnySpiffeId && haveAllowList) {
            throw new IllegalStateException(
                    "acceptAnySpiffeId and acceptedSpiffeIds are mutually exclusive; set only one");
        }
        if (!acceptAnySpiffeId && !haveAllowList) {
            throw new IllegalStateException(
                    "SpiffeSecurityPolicy requires either acceptAnySpiffeId=true or a non-empty acceptedSpiffeIds"
                                            + " allow-list");
        }
        Set<SpiffeId> parsed = haveAllowList ? parseSpiffeIds(acceptedSpiffeIds) : Set.of();
        if (!acceptAnySpiffeId && parsed.isEmpty()) {
            throw new IllegalStateException("acceptedSpiffeIds did not contain any SPIFFE ID after trimming");
        }
        this.acceptedSet = parsed;
    }

    @Override
    public Processor wrap(Route route, Processor processor) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Securing route {} with SPIFFE policy (acceptAnySpiffeId={}, acceptedSpiffeIds={})",
                    route.getRouteId(), acceptAnySpiffeId, acceptedSet);
        }
        return new SpiffeSecurityProcessor(processor, this);
    }

    /**
     * Whether the given verified peer SPIFFE ID is authorized by this policy.
     */
    boolean isAuthorized(SpiffeId peer) {
        return acceptAnySpiffeId || acceptedSet.contains(peer);
    }

    static Set<SpiffeId> parseSpiffeIds(String csv) {
        Set<SpiffeId> ids = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                ids.add(SpiffeId.parse(trimmed));
            }
        }
        return ids;
    }

    public String getAcceptedSpiffeIds() {
        return acceptedSpiffeIds;
    }

    /**
     * Comma-separated allow-list of peer SPIFFE IDs this route segment authorizes (for example
     * {@code spiffe://example.org/frontend}). Mutually exclusive with {@code acceptAnySpiffeId}.
     */
    public void setAcceptedSpiffeIds(String acceptedSpiffeIds) {
        this.acceptedSpiffeIds = acceptedSpiffeIds;
    }

    public boolean isAcceptAnySpiffeId() {
        return acceptAnySpiffeId;
    }

    /**
     * Authorize any peer that presents a verified SPIFFE SVID, instead of an explicit {@code acceptedSpiffeIds}
     * allow-list. The handshake still has to have authenticated the peer against the trust bundle, so this asserts only
     * "a verified SPIFFE peer exists" and leaves the which-peer decision to a downstream policy (for example OPA).
     * Still fails closed when no verified peer certificate is present. Mutually exclusive with
     * {@code acceptedSpiffeIds}; when neither is set the policy refuses to start.
     */
    public void setAcceptAnySpiffeId(boolean acceptAnySpiffeId) {
        this.acceptAnySpiffeId = acceptAnySpiffeId;
    }

    public String getSslSessionHeader() {
        return sslSessionHeader;
    }

    /**
     * Name of the message header that carries the verified {@link javax.net.ssl.SSLSession} from which the peer
     * certificate is read. Defaults to {@code CamelNettySSLSession}, the header camel-netty-http populates; override it
     * for another TLS consumer that exposes the session under a different name.
     */
    public void setSslSessionHeader(String sslSessionHeader) {
        this.sslSessionHeader = sslSessionHeader;
    }
}
