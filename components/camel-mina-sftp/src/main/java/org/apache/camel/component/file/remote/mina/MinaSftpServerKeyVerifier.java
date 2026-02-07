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
package org.apache.camel.component.file.remote.mina;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;
import org.apache.sshd.client.config.hosts.KnownHostEntry;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.OpenSshCertificate;
import org.apache.sshd.common.util.GenericUtils;

/**
 * ServerKeyVerifier implementation for SFTP host key verification.
 * <p/>
 * Extends MINA SSHD's {@link KnownHostsServerKeyVerifier} to add:
 * <ul>
 * <li>Multi-source known_hosts loading (byte array, URI, file, user file)</li>
 * <li>Full OpenSSH host certificate validation (type, validity, principals)</li>
 * <li>Camel-specific configuration integration</li>
 * </ul>
 * <p/>
 * Priority order for loading known hosts:
 * <ol>
 * <li>Byte array (knownHosts configuration)</li>
 * <li>Camel resource URI (knownHostsUri)</li>
 * <li>File path (knownHostsFile)</li>
 * <li>User's ~/.ssh/known_hosts (useUserKnownHostsFile)</li>
 * </ol>
 */
public class MinaSftpServerKeyVerifier extends KnownHostsServerKeyVerifier {

    private static final Path USER_KNOWN_HOSTS_PATH = Paths.get(
            System.getProperty("user.home"), ".ssh", "known_hosts");

    private final CamelContext camelContext;
    private final MinaSftpConfiguration configuration;
    private volatile Collection<HostEntryPair> cachedEntries;
    private volatile boolean customEntriesLoaded = false;

    public MinaSftpServerKeyVerifier(CamelContext camelContext, MinaSftpConfiguration configuration) {
        // Use AcceptAllServerKeyVerifier as delegate for unknown hosts handling
        // We override acceptUnknownHostKey() to implement our own logic
        super(AcceptAllServerKeyVerifier.INSTANCE, determineKnownHostsPath(configuration));
        this.camelContext = camelContext;
        this.configuration = configuration;
    }

    /**
     * Determine the known_hosts file path to use.
     * <p/>
     * This is used by the parent class constructor. We prefer:
     * <ol>
     * <li>knownHostsFile if specified</li>
     * <li>User's known_hosts if useUserKnownHostsFile is enabled</li>
     * <li>A non-existent path (we'll load entries via other means)</li>
     * </ol>
     */
    private static Path determineKnownHostsPath(MinaSftpConfiguration configuration) {
        if (configuration.getKnownHostsFile() != null && !configuration.getKnownHostsFile().isEmpty()) {
            return Paths.get(configuration.getKnownHostsFile());
        }
        if (configuration.isUseUserKnownHostsFile()) {
            return USER_KNOWN_HOSTS_PATH;
        }
        // Return a dummy path - we'll load entries ourselves
        return Paths.get(System.getProperty("java.io.tmpdir"), ".camel-mina-sftp-known_hosts");
    }

    @Override
    public boolean verifyServerKey(ClientSession clientSession, SocketAddress remoteAddress, PublicKey serverKey) {
        // Load entries from our multi-source configuration

        if (!customEntriesLoaded) {
            try {
                List<HostEntryPair> entries = reloadKnownHosts(clientSession, getPath());
                setLoadedHostsEntries(entries);
                cachedEntries = entries;
                customEntriesLoaded = true;
                log.debug("Loaded {} known hosts entries", entries.size());
            } catch (IOException | GeneralSecurityException e) {
                log.warn("Failed to load known hosts entries: {}", e.getMessage());
                cachedEntries = java.util.Collections.emptyList();
            }
        }

        // For non-file sources (byte array, URI), we handle verification ourselves
        // because parent's exists() check would return false and overwrite our entries
        boolean hasNonFileSource = (configuration.getKnownHosts() != null && configuration.getKnownHosts().length > 0)
                || (configuration.getKnownHostsUri() != null
                        && !configuration.getKnownHostsUri().isEmpty());

        if (hasNonFileSource) {
            // Call acceptKnownHostEntries directly with our cached entries
            return acceptKnownHostEntries(clientSession, remoteAddress, serverKey, cachedEntries);
        }

        // For file-based sources, delegate to parent (it handles file watching, etc.)
        return super.verifyServerKey(clientSession, remoteAddress, serverKey);
    }

    @Override
    protected List<HostEntryPair> reloadKnownHosts(ClientSession session, Path file)
            throws IOException, GeneralSecurityException {
        List<HostEntryPair> entries = new ArrayList<>();

        // Priority 1: Byte array (knownHosts)
        if (configuration.getKnownHosts() != null && configuration.getKnownHosts().length > 0) {
            entries.addAll(loadFromBytes(session, configuration.getKnownHosts()));
            log.debug("Loaded {} known hosts entries from byte array", entries.size());
            customEntriesLoaded = true;
            return entries;
        }

        // Priority 2: Camel resource URI (knownHostsUri)
        if (configuration.getKnownHostsUri() != null && !configuration.getKnownHostsUri().isEmpty()) {
            List<HostEntryPair> uriEntries = loadFromUri(session, configuration.getKnownHostsUri());
            if (!uriEntries.isEmpty()) {
                entries.addAll(uriEntries);
                log.debug("Loaded {} known hosts entries from URI: {}",
                        entries.size(), configuration.getKnownHostsUri());
                customEntriesLoaded = true;
                return entries;
            }
        }

        // Priority 3: File path (knownHostsFile)
        if (configuration.getKnownHostsFile() != null && !configuration.getKnownHostsFile().isEmpty()) {
            Path knownHostsFile = Paths.get(configuration.getKnownHostsFile());
            if (Files.exists(knownHostsFile) && Files.isReadable(knownHostsFile)) {
                entries.addAll(super.reloadKnownHosts(session, knownHostsFile));
                log.debug("Loaded {} known hosts entries from file: {}",
                        entries.size(), configuration.getKnownHostsFile());
                customEntriesLoaded = true;
                return entries;
            }
        }

        // Priority 4: User's ~/.ssh/known_hosts
        if (configuration.isUseUserKnownHostsFile()) {
            if (Files.exists(USER_KNOWN_HOSTS_PATH) && Files.isReadable(USER_KNOWN_HOSTS_PATH)) {
                entries.addAll(super.reloadKnownHosts(session, USER_KNOWN_HOSTS_PATH));
                log.debug("Loaded {} known hosts entries from user file: {}",
                        entries.size(), USER_KNOWN_HOSTS_PATH);
                customEntriesLoaded = true;
                return entries;
            } else if (configuration.isAutoCreateKnownHostsFile()) {
                log.debug("User known_hosts file does not exist, will auto-create on first connection");
            }
        }

        return entries;
    }

    /**
     * Load known hosts entries from a byte array.
     */
    private List<HostEntryPair> loadFromBytes(ClientSession session, byte[] data)
            throws IOException, GeneralSecurityException {
        List<HostEntryPair> entries = new ArrayList<>();
        try (InputStream is = new ByteArrayInputStream(data)) {
            Collection<KnownHostEntry> loaded = KnownHostEntry.readKnownHostEntries(is, true);
            for (KnownHostEntry entry : loaded) {
                PublicKey key = resolveHostKey(session, entry, getFallbackPublicKeyEntryResolver());
                if (key != null) {
                    entries.add(new HostEntryPair(entry, key));
                }
            }
        }
        return entries;
    }

    /**
     * Load known hosts entries from a Camel resource URI.
     */
    private List<HostEntryPair> loadFromUri(ClientSession session, String uri)
            throws IOException, GeneralSecurityException {
        List<HostEntryPair> entries = new ArrayList<>();
        try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, uri)) {
            Collection<KnownHostEntry> loaded = KnownHostEntry.readKnownHostEntries(is, true);
            for (KnownHostEntry entry : loaded) {
                PublicKey key = resolveHostKey(session, entry, getFallbackPublicKeyEntryResolver());
                if (key != null) {
                    entries.add(new HostEntryPair(entry, key));
                }
            }
        } catch (IOException e) {
            log.warn("Cannot read known_hosts from URI {}: {}", uri, e.getMessage());
        }
        return entries;
    }

    @Override
    protected boolean acceptKnownHostEntries(
            ClientSession clientSession, SocketAddress remoteAddress, PublicKey serverKey,
            Collection<HostEntryPair> knownHosts) {

        // If server presented a certificate, perform additional validation
        // that MINA SSHD doesn't do: type, validity, principals
        if (serverKey instanceof OpenSshCertificate) {
            OpenSshCertificate certificate = (OpenSshCertificate) serverKey;
            String hostname = extractHostname(remoteAddress);
            int port = extractPort(remoteAddress);

            // Check if there are any @cert-authority entries for this host
            boolean hasCaEntries = knownHosts.stream()
                    .anyMatch(e -> "cert-authority".equals(e.getHostEntry().getMarker())
                            && e.getHostEntry().isHostMatch(hostname, port));

            if (hasCaEntries) {
                // Perform our additional certificate validation before delegating to parent
                CertificateValidationResult result = validateHostCertificate(certificate, hostname, port);
                if (!result.isValid()) {
                    log.error("Host certificate validation failed for {}:{}: {}",
                            hostname, port, result.getMessage());
                    return false;
                }
                log.debug("Host certificate validation passed for {}:{}", hostname, port);
            }
        }

        // Delegate to parent for CA key matching and regular host key verification
        return super.acceptKnownHostEntries(clientSession, remoteAddress, serverKey, knownHosts);
    }

    /**
     * Handle unknown host keys (hosts not in known_hosts).
     * <p/>
     * We override this method instead of using the delegate pattern (AcceptAllServerKeyVerifier vs
     * RejectAllServerKeyVerifier) for several reasons:
     * <ul>
     * <li>Custom logging: Provides clear, actionable error messages for users</li>
     * <li>Security warnings: Explicitly warns when auto-creating known_hosts entries</li>
     * <li>Fine-grained control: Separates strictHostKeyChecking from autoCreateKnownHostsFile logic</li>
     * <li>Camel integration: Uses Camel's configuration model rather than MINA's delegate pattern</li>
     * </ul>
     */
    @Override
    protected boolean acceptUnknownHostKey(ClientSession clientSession, SocketAddress remoteAddress, PublicKey serverKey) {
        String hostname = extractHostname(remoteAddress);
        int port = extractPort(remoteAddress);
        boolean strictChecking = "yes".equalsIgnoreCase(configuration.getStrictHostKeyChecking());

        if (strictChecking) {
            log.error("Host key verification failed: server '{}:{}' is not in the known_hosts file. "
                      + "Add the host key or set strictHostKeyChecking=no.",
                    hostname, port);
            return false;
        }

        log.warn("Server '{}:{}' is not in the known_hosts file. "
                 + "Accepting connection because strictHostKeyChecking=no.",
                hostname, port);

        // If autoCreateKnownHostsFile is enabled, delegate to parent to save the key
        if (configuration.isAutoCreateKnownHostsFile()) {
            log.warn("SECURITY WARNING: Auto-creating known_hosts entry for {}:{}. "
                     + "This is only recommended for development environments.",
                    hostname, port);
            return super.acceptUnknownHostKey(clientSession, remoteAddress, serverKey);
        }

        return true;
    }

    /**
     * Validate an OpenSSH host certificate.
     * <p/>
     * MINA SSHD's KnownHostsServerKeyVerifier only checks if the CA key matches. This method adds:
     * <ul>
     * <li>Certificate type validation (must be HOST)</li>
     * <li>Validity period check (validAfter/validBefore)</li>
     * <li>Principal matching (hostname must be in certificate principals)</li>
     * </ul>
     */
    private CertificateValidationResult validateHostCertificate(
            OpenSshCertificate certificate, String hostname, int port) {

        // Step 1: Check certificate type (must be HOST certificate)
        OpenSshCertificate.Type certType = certificate.getType();
        if (certType != OpenSshCertificate.Type.HOST) {
            return CertificateValidationResult.failure(
                    String.format("Invalid certificate type: expected HOST certificate, got %s", certType));
        }

        // Step 2: Check validity period using MINA SSHD's built-in method
        if (!OpenSshCertificate.isValidNow(certificate)) {
            long validAfter = certificate.getValidAfter();
            long validBefore = certificate.getValidBefore();
            return CertificateValidationResult.failure(
                    String.format("Host certificate is not valid. Valid from %s to %s, current time: %s",
                            validAfter > 0 ? Instant.ofEpochSecond(validAfter) : "epoch",
                            validBefore == OpenSshCertificate.INFINITY
                                    ? "forever"
                                    : Instant.ofEpochSecond(validBefore),
                            Instant.now()));
        }

        // Step 3: Check principals (hostname must be in the list)
        Collection<String> principals = certificate.getPrincipals();
        if (!GenericUtils.isEmpty(principals)) {
            boolean principalMatch = matchesPrincipal(hostname, port, principals);
            if (!principalMatch) {
                return CertificateValidationResult.failure(
                        String.format("Hostname '%s' is not listed in certificate principals: %s",
                                hostname, principals));
            }
        }
        // Note: Empty principals list is acceptable (matches any host)

        return CertificateValidationResult.success();
    }

    /**
     * Check if hostname matches any of the certificate principals.
     */
    private boolean matchesPrincipal(String hostname, int port, Collection<String> principals) {
        for (String principal : principals) {
            // Direct hostname match
            if (principal.equals(hostname)) {
                return true;
            }
            // [hostname]:port format
            if (principal.equals("[" + hostname + "]:" + port)) {
                return true;
            }
            // Wildcard match (e.g., *.example.com)
            if (principal.startsWith("*.") && hostname.endsWith(principal.substring(1))) {
                return true;
            }
        }
        return false;
    }

    private String extractHostname(SocketAddress remoteAddress) {
        if (remoteAddress instanceof InetSocketAddress) {
            return ((InetSocketAddress) remoteAddress).getHostString();
        }
        return remoteAddress.toString();
    }

    private int extractPort(SocketAddress remoteAddress) {
        if (remoteAddress instanceof InetSocketAddress) {
            return ((InetSocketAddress) remoteAddress).getPort();
        }
        return 22;
    }

    /**
     * Result of certificate validation.
     */
    private static class CertificateValidationResult {
        private final boolean valid;
        private final String message;

        private CertificateValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        static CertificateValidationResult success() {
            return new CertificateValidationResult(true, null);
        }

        static CertificateValidationResult failure(String message) {
            return new CertificateValidationResult(false, message);
        }

        boolean isValid() {
            return valid;
        }

        String getMessage() {
            return message;
        }
    }
}
