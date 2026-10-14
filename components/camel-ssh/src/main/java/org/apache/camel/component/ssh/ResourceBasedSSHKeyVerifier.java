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
package org.apache.camel.component.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.sshd.client.config.hosts.KnownHostEntry;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ServerKeyVerifier that takes a camel resource as input file to validate the server key against.
 * <p/>
 * The resource uses the OpenSSH known_hosts format. The entries are matched against the host name (or address) and port
 * the client connects to, as configured on the endpoint: host patterns are matched as a whole, wildcard, negated and
 * hashed patterns are supported, and an entry without a port only applies to port 22. The server is accepted when one
 * of the matching entries holds the key it presents, and rejected when that key is marked as {@code @revoked} or when
 * the host has entries but none of them holds that key. Only a host without any matching entry is subject to
 * {@code failOnUnknownHost}. Host certificates ({@code @cert-authority} entries) are not supported.
 */
public class ResourceBasedSSHKeyVerifier implements ServerKeyVerifier {
    private static final String MARKER_REVOKED = "revoked";
    private static final String MARKER_CERT_AUTHORITY = "cert-authority";

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private CamelContext camelContext;
    private boolean failOnUnknownHost;
    private String knownHostsResource;

    public ResourceBasedSSHKeyVerifier(CamelContext camelContext, String knownHostsResource) {
        this(camelContext, knownHostsResource, false);
    }

    public ResourceBasedSSHKeyVerifier(CamelContext camelContext, String knownHostsResource,
                                       boolean failOnUnknownHost) {
        this.camelContext = camelContext;
        this.knownHostsResource = knownHostsResource;
        this.failOnUnknownHost = failOnUnknownHost;
    }

    @Override
    public boolean verifyServerKey(ClientSession sshClientSession, SocketAddress remoteAddress, PublicKey serverKey) {
        log.debug("Trying to find known_hosts file {}", knownHostsResource);
        List<KnownHostEntry> entries = Collections.emptyList();
        InputStream knownHostsInputStream = null;
        try {
            knownHostsInputStream = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext,
                    knownHostsResource);
            entries = KnownHostEntry.readKnownHostEntries(knownHostsInputStream, false);
        } catch (IOException ioException) {
            log.debug(String.format("Could not find known_hosts file %s", knownHostsResource), ioException);
        } finally {
            IOHelper.close(knownHostsInputStream);
        }

        SshdSocketAddress server = toServerAddress(remoteAddress);
        boolean hostListed = false;
        boolean keyListed = false;
        if (server != null) {
            log.debug("Trying to match PublicKey of {} against provided known_hosts file", server);
            for (KnownHostEntry entry : entries) {
                if (!entry.isHostMatch(server.getHostName(), server.getPort())
                        || MARKER_CERT_AUTHORITY.equals(entry.getMarker())) {
                    continue;
                }
                boolean sameKey = isSameKey(sshClientSession, entry, serverKey);
                if (MARKER_REVOKED.equals(entry.getMarker())) {
                    if (sameKey) {
                        log.warn("The key presented by {} is marked as revoked in the known_hosts file {}", server,
                                knownHostsResource);
                        return false;
                    }
                } else {
                    hostListed = true;
                    if (sameKey) {
                        keyListed = true;
                    }
                }
            }
        }

        if (keyListed) {
            log.debug("Found PublicKey match for server {}", server);
            return true;
        }
        if (hostListed) {
            log.warn("The key presented by {} does not match any key listed for it in the known_hosts file {}", server,
                    knownHostsResource);
            return false;
        }
        if (failOnUnknownHost) {
            log.warn("Could not find matching key for client session, connection will fail due to configuration");
            return false;
        } else {
            log.warn(
                    "Could not find matching key for client session, connection will continue anyway due to configuration");
            return true;
        }
    }

    private boolean isSameKey(ClientSession sshClientSession, KnownHostEntry entry, PublicKey serverKey) {
        String keyType = entry.getKeyEntry().getKeyType();
        try {
            PublicKey key = entry.getKeyEntry().resolvePublicKey(sshClientSession, PublicKeyEntryResolver.IGNORING);
            if (key == null) {
                log.warn("Unsupported key type {} in the known_hosts file {}", keyType, knownHostsResource);
                return false;
            }
            return KeyUtils.compareKeys(key, serverKey);
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            // a key that cannot be decoded cannot be the key the server presented
            log.warn("Could not load a {} key from the known_hosts file {}", keyType, knownHostsResource, e);
            return false;
        }
    }

    /*
     * The host name or address and the port the client connected to. The host is used exactly as given, never
     * resolved, so an endpoint configured with an address is not looked up by the name a reverse lookup returns.
     */
    private static SshdSocketAddress toServerAddress(SocketAddress remoteAddress) {
        if (remoteAddress instanceof InetSocketAddress || remoteAddress instanceof SshdSocketAddress) {
            return SshdSocketAddress.toSshdSocketAddress(remoteAddress);
        }
        return null;
    }

}
