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
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Collections;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.ClientSessionCreator;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ServerKeyVerifier that takes a camel resource as input file to validate the server key against.
 * <p>
 * The resource uses the OpenSSH known_hosts format, and its entries are matched as OpenSSH matches them. Each host
 * pattern of an entry is compared, ignoring case, with the host name or address the client connects to (the host
 * configured on the endpoint, or the {@code HostName} that {@code ~/.ssh/config} gives for it, as it is written and
 * never resolved through DNS, so an IPv6 address such as {@code ::1} is not expanded), written {@code host} for port 22
 * and {@code [host]:port} for another port: {@code *} and {@code ?} are wildcards, a pattern starting with {@code !}
 * excludes the hosts it matches, and hashed entries are supported. The server is accepted when an entry of its host
 * lists the key it presents. It is rejected when that key is listed in a matching {@code @revoked} entry, or when its
 * host has entries without a marker but none of them lists that key. {@code failOnUnknownHost} only decides for a host
 * without such an entry: {@code @revoked} and {@code @cert-authority} entries do not list a host, and host certificates
 * are not supported. A resource that cannot be read, or that has a line that cannot be read, rejects every server.
 */
public class ResourceBasedSSHKeyVerifier implements ServerKeyVerifier {
    private static final int DEFAULT_PORT = 22;
    private static final String MARKER_REVOKED = "@revoked";
    private static final String MARKER_CERT_AUTHORITY = "@cert-authority";
    private static final String HASHED_HOST_PREFIX = "|1|";
    private static final String HASH_ALGORITHM = "HmacSHA1";
    private static final int HASH_LENGTH = 20;

    /**
     * The host configuration that the client connected with, which the ssh producer and consumer put in the connection
     * context of the session: the entries are matched against its {@code HostName} as it is written. Without it, the
     * address the client connected to is used, which writes an IPv6 address such as {@code ::1} in its full form.
     *
     * @since 4.24
     */
    public static final AttributeRepository.AttributeKey<HostConfigEntry> HOST_CONFIG_ENTRY
            = new AttributeRepository.AttributeKey<>();

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
        String knownHosts;
        try (InputStream knownHostsInputStream
                = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, knownHostsResource)) {
            knownHosts = new String(knownHostsInputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ioException) {
            log.warn("Could not read the known_hosts file {}, connection will fail", knownHostsResource, ioException);
            return false;
        }

        String server = toKnownHostsName(sshClientSession, remoteAddress);
        boolean hostListed = false;
        boolean keyListed = false;
        int lineNumber = 0;
        for (String line : knownHosts.split("\\R")) {
            lineNumber++;
            String text = line.trim();
            if (text.isEmpty() || text.charAt(0) == '#') {
                continue;
            }
            // a line that cannot be read could list the server, so it cannot be skipped
            KnownHostsEntry entry = KnownHostsEntry.parse(text, sshClientSession);
            if (entry == null) {
                log.warn("Line {} of the known_hosts file {} cannot be read, connection will fail", lineNumber,
                        knownHostsResource);
                return false;
            }
            if (server == null || MARKER_CERT_AUTHORITY.equals(entry.marker) || !entry.matches(server)) {
                continue;
            }
            boolean sameKey = isSameKey(entry, serverKey);
            if (MARKER_REVOKED.equals(entry.marker)) {
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

    private boolean isSameKey(KnownHostsEntry entry, PublicKey serverKey) {
        if (entry.key == null) {
            log.warn("Unsupported key type {} in the known_hosts file {}", entry.keyType, knownHostsResource);
            return false;
        }
        return KeyUtils.compareKeys(entry.key, serverKey);
    }

    /*
     * The name OpenSSH looks the server up by: the host name or address the client connected to, as the host
     * configuration writes it and never resolved through DNS, with the port the client connected to, written host for
     * port 22 and [host]:port for another port. The connected address cannot give the host as it is written, as it
     * writes an IPv6 address such as ::1 in its full form.
     */
    private static String toKnownHostsName(ClientSession session, SocketAddress remoteAddress) {
        if (!(remoteAddress instanceof InetSocketAddress) && !(remoteAddress instanceof SshdSocketAddress)) {
            return null;
        }
        SshdSocketAddress address = SshdSocketAddress.toSshdSocketAddress(remoteAddress);
        String name = address.getHostName();
        SshdSocketAddress target = session != null ? session.getAttribute(ClientSessionCreator.TARGET_SERVER) : null;
        AttributeRepository context = session != null ? session.getConnectionContext() : null;
        HostConfigEntry hostConfig = context != null ? context.getAttribute(HOST_CONFIG_ENTRY) : null;
        if (target != null) {
            // a host reached through a jump host, whose HostName SSHD keeps as it is written
            name = target.getHostName();
        } else if (hostConfig != null && (hostConfig.getProxyJump() == null || hostConfig.getProxyJump().isEmpty())) {
            // the host the client connected to directly, with the host configuration it connected with
            name = hostConfig.getHostName();
        }
        if (name.length() > 1 && name.charAt(0) == '[' && name.charAt(name.length() - 1) == ']') {
            // an IPv6 address in brackets
            name = name.substring(1, name.length() - 1);
        }
        name = name.toLowerCase(Locale.ROOT);
        return address.getPort() == DEFAULT_PORT ? name : "[" + name + "]:" + address.getPort();
    }

    /*
     * Matches a pattern where * stands for any sequence of characters and ? for any single character.
     */
    static boolean matchesPattern(String pattern, String name) {
        int p = 0;
        int n = 0;
        int star = -1;
        int resume = 0;
        while (n < name.length()) {
            if (p < pattern.length() && (pattern.charAt(p) == '?' || pattern.charAt(p) == name.charAt(n))) {
                p++;
                n++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                resume = n;
            } else if (star >= 0) {
                p = star + 1;
                n = ++resume;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') {
            p++;
        }
        return p == pattern.length();
    }

    /*
     * A line of the known_hosts file: an optional marker, the host patterns or a hashed host, and the key. Anything
     * after the key is a comment.
     */
    private static final class KnownHostsEntry {
        private final String marker;
        private final String hosts;
        private final byte[] salt;
        private final byte[] hash;
        private final String keyType;
        private final PublicKey key;

        private KnownHostsEntry(String marker, String hosts, byte[] salt, byte[] hash, String keyType, PublicKey key) {
            this.marker = marker;
            this.hosts = hosts;
            this.salt = salt;
            this.hash = hash;
            this.keyType = keyType;
            this.key = key;
        }

        /*
         * Returns null when the line cannot be read. A key of a type that is not supported is kept as a null key.
         */
        static KnownHostsEntry parse(String line, SessionContext session) {
            String[] fields = line.split("\\s+");
            int index = 0;
            String marker = null;
            if (fields[0].startsWith("@")) {
                marker = fields[0];
                if (!MARKER_REVOKED.equals(marker) && !MARKER_CERT_AUTHORITY.equals(marker)) {
                    return null;
                }
                index = 1;
            }
            if (fields.length < index + 3) {
                return null;
            }
            String hosts = fields[index];
            try {
                byte[] salt = null;
                byte[] hash = null;
                if (hosts.startsWith("|")) {
                    // a hashed host: only |1|salt|hash, with a SHA-1 salt and hash, is supported
                    if (!hosts.startsWith(HASHED_HOST_PREFIX)) {
                        return null;
                    }
                    String[] parts = hosts.substring(HASHED_HOST_PREFIX.length()).split("\\|", -1);
                    if (parts.length != 2) {
                        return null;
                    }
                    salt = Base64.getDecoder().decode(parts[0]);
                    hash = Base64.getDecoder().decode(parts[1]);
                    if (salt.length != HASH_LENGTH || hash.length != HASH_LENGTH) {
                        return null;
                    }
                }
                PublicKeyEntry keyEntry = PublicKeyEntry.parsePublicKeyEntry(fields[index + 1] + " " + fields[index + 2]);
                PublicKey key = keyEntry.resolvePublicKey(session, Collections.emptyMap(), PublicKeyEntryResolver.IGNORING);
                return new KnownHostsEntry(marker, hosts, salt, hash, keyEntry.getKeyType(), key);
            } catch (IOException | GeneralSecurityException | RuntimeException e) {
                return null;
            }
        }

        boolean matches(String name) {
            if (hash != null) {
                return MessageDigest.isEqual(hash, hashOf(name));
            }
            boolean matched = false;
            for (String pattern : hosts.split(",")) {
                boolean negated = pattern.startsWith("!");
                String hostPattern = (negated ? pattern.substring(1) : pattern).toLowerCase(Locale.ROOT);
                if (!hostPattern.isEmpty() && matchesPattern(hostPattern, name)) {
                    if (negated) {
                        return false;
                    }
                    matched = true;
                }
            }
            return matched;
        }

        private byte[] hashOf(String name) {
            try {
                Mac mac = Mac.getInstance(HASH_ALGORITHM);
                mac.init(new SecretKeySpec(salt, HASH_ALGORITHM));
                return mac.doFinal(name.getBytes(StandardCharsets.UTF_8));
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Cannot compute the hash of a known_hosts host", e);
            }
        }
    }

}
