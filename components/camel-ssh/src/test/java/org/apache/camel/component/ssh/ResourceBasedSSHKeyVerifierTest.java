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

import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.ClientSessionCreator;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.common.util.io.IoUtils;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceBasedSSHKeyVerifierTest {

    @TempDir
    Path dir;

    private CamelContext context;
    private PublicKey serverKey;
    private PublicKey otherKey;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        serverKey = generator.generateKeyPair().getPublic();
        otherKey = generator.generateKeyPair().getPublic();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void acceptsTheKeyListedForTheHost() throws Exception {
        assertTrue(verify(true, "db.example.com", 22, serverKey, entry("db.example.com", serverKey)));
        assertTrue(verify(true, "db.example.com", 22, serverKey, entry("web.example.com,db.example.com", serverKey)));
    }

    @Test
    void doesNotUseTheEntryOfAHostWhoseNameContainsTheHostName() throws Exception {
        assertFalse(verify(true, "db.example.com", 22, otherKey, entry("old-db.example.com", otherKey)));
        assertFalse(verify(true, "db.example.com", 22, otherKey, entry("db.example.com.old", otherKey)));
    }

    @Test
    void doesNotUseTheEntryOfAnAddressContainingTheAddress() throws Exception {
        String knownHosts = entry("10.0.0.12", otherKey) + entry("10.0.0.1", serverKey);
        assertFalse(verify(true, "10.0.0.1", 22, otherKey, knownHosts));
        assertTrue(verify(true, "10.0.0.1", 22, serverKey, knownHosts));
        assertFalse(verify(true, "10.0.0.1", 22, otherKey, entry("110.0.0.1", otherKey)));
    }

    @Test
    void matchesThePort() throws Exception {
        String knownHosts = entry("[db.example.com]:2222", otherKey);
        assertFalse(verify(true, "db.example.com", 22, otherKey, knownHosts));
        assertFalse(verify(true, "db.example.com", 222, otherKey, knownHosts));
        assertTrue(verify(true, "db.example.com", 2222, otherKey, knownHosts));
        // an entry without a port only applies to the default port
        assertFalse(verify(true, "db.example.com", 2222, serverKey, entry("db.example.com", serverKey)));
    }

    @Test
    void matchesPatternsAgainstTheHostAndPortAsOpenSshWritesThem() throws Exception {
        assertTrue(verify(true, "db.example.com", 2222, serverKey, entry("*", serverKey)));
        assertTrue(verify(true, "db.example.com", 2222, serverKey, entry("[*.example.com]:2222", serverKey)));
        assertFalse(verify(true, "db.example.com", 2222, serverKey, entry("*.example.com", serverKey)));
        assertTrue(verify(true, "db.example.com", 2222, serverKey, entry("[db.example.com]:22?2", serverKey)));
    }

    @Test
    void matchesNegatedPortQualifiedPatterns() throws Exception {
        String knownHosts = entry("![excluded.example.com]:2222,[db.example.com]:2222", serverKey);
        for (boolean failOnUnknownHost : new boolean[] { true, false }) {
            assertTrue(verify(failOnUnknownHost, "db.example.com", 2222, serverKey, knownHosts));
            assertFalse(verify(failOnUnknownHost, "db.example.com", 2222, otherKey, knownHosts));
        }
        assertFalse(verify(true, "excluded.example.com", 2222, serverKey, knownHosts));
    }

    @Test
    void matchesWildcardNegatedAndUpperCasePatterns() throws Exception {
        assertTrue(verify(true, "db.example.com", 22, serverKey, entry("*.example.com", serverKey)));
        assertTrue(verify(true, "db.example.com", 22, serverKey, entry("DB.EXAMPLE.COM", serverKey)));
        String knownHosts = entry("!db.example.com,*.example.com", otherKey);
        assertFalse(verify(true, "db.example.com", 22, otherKey, knownHosts));
        assertTrue(verify(true, "www.example.com", 22, otherKey, knownHosts));
    }

    @Test
    void matchesHashedEntries() throws Exception {
        String knownHosts = entry(hashed("db.example.com"), serverKey);
        assertTrue(verify(true, "db.example.com", 22, serverKey, knownHosts));
        assertFalse(verify(true, "db.example.com", 22, otherKey, knownHosts));
        assertFalse(verify(true, "www.example.com", 22, serverKey, knownHosts));
        assertFalse(verify(true, "db.example.com", 2222, serverKey, knownHosts));
        assertTrue(verify(true, "db.example.com", 2222, serverKey, entry(hashed("[db.example.com]:2222"), serverKey)));
    }

    @Test
    void considersEveryEntryOfTheHost() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        PublicKey rsaKey = generator.generateKeyPair().getPublic();
        String knownHosts = entry("db.example.com", rsaKey) + entry("db.example.com", serverKey);
        assertTrue(verify(true, "db.example.com", 22, serverKey, knownHosts));
        assertTrue(verify(true, "db.example.com", 22, rsaKey, knownHosts));
    }

    @Test
    void readsCommentsAndCrlfLineEndings() throws Exception {
        String knownHosts = "# known hosts\r\n" + "db.example.com " + PublicKeyEntry.toString(serverKey) + " db key\r\n";
        assertTrue(verify(true, "db.example.com", 22, serverKey, knownHosts));
    }

    @Test
    void rejectsARevokedKey() throws Exception {
        String knownHosts = "@revoked * " + PublicKeyEntry.toString(otherKey) + "\n" + entry("db.example.com", otherKey);
        assertFalse(verify(true, "db.example.com", 22, otherKey, knownHosts));
        assertFalse(verify(false, "db.example.com", 22, otherKey, knownHosts));
        assertTrue(verify(true, "db.example.com", 22, serverKey, knownHosts + entry("db.example.com", serverKey)));
    }

    @Test
    void rejectsARevokedKeyOnEveryPortAndInEveryOrder() throws Exception {
        String revoked = "@revoked * " + PublicKeyEntry.toString(serverKey) + "\n";
        for (int port : new int[] { 22, 2222 }) {
            String listed = entry(port == 22 ? "db.example.com" : "[db.example.com]:" + port, serverKey);
            for (boolean failOnUnknownHost : new boolean[] { true, false }) {
                assertFalse(verify(failOnUnknownHost, "db.example.com", port, serverKey, revoked + listed),
                        () -> "revocation before the entry, port " + port + ", failOnUnknownHost " + failOnUnknownHost);
                assertFalse(verify(failOnUnknownHost, "db.example.com", port, serverKey, listed + revoked),
                        () -> "revocation after the entry, port " + port + ", failOnUnknownHost " + failOnUnknownHost);
            }
        }
    }

    @Test
    void treatsAHostWithOnlyRevokedEntriesAsUnknown() throws Exception {
        // as OpenSSH does: a revoked entry rejects its key but does not list the host
        String knownHosts = "@revoked db.example.com " + PublicKeyEntry.toString(otherKey) + "\n";
        assertFalse(verify(true, "db.example.com", 22, serverKey, knownHosts));
        assertTrue(verify(false, "db.example.com", 22, serverKey, knownHosts));
        assertFalse(verify(false, "db.example.com", 22, otherKey, knownHosts));
    }

    @Test
    void rejectsEveryServerWhenALineCannotBeRead() throws Exception {
        String key = PublicKeyEntry.toString(serverKey);
        String[] unreadableLines = {
                "@unknown db.example.com " + key,
                "@revoked",
                "db.example.com",
                "db.example.com ssh-ed25519 not*base64",
                "|1|c2FsdA==|aGFzaA== " + key,
                "|1|" + base64(20) + "| " + key,
                "|1|" + base64(20) + "|" + base64(19) + " " + key,
                "|1|" + base64(20) + "|" + base64(21) + " " + key,
                "|2|" + base64(20) + "|" + base64(20) + " " + key };
        for (String line : unreadableLines) {
            for (String knownHosts : new String[] { line + "\n", entry("db.example.com", serverKey) + line + "\n" }) {
                for (boolean failOnUnknownHost : new boolean[] { true, false }) {
                    assertFalse(verify(failOnUnknownHost, "db.example.com", 22, serverKey, knownHosts), line);
                    assertFalse(verify(failOnUnknownHost, "www.example.com", 22, serverKey, knownHosts), line);
                }
            }
        }
    }

    @Test
    void rejectsEveryServerWhenTheResourceCannotBeRead() {
        for (boolean failOnUnknownHost : new boolean[] { true, false }) {
            ResourceBasedSSHKeyVerifier verifier
                    = new ResourceBasedSSHKeyVerifier(context, "file:" + dir.resolve("missing"), failOnUnknownHost);
            assertFalse(verifier.verifyServerKey(null, new InetSocketAddress("127.0.0.1", 22), serverKey));
        }
    }

    @Test
    void ignoresCertificateAuthorityEntries() throws Exception {
        String knownHosts = "@cert-authority db.example.com " + PublicKeyEntry.toString(serverKey) + "\n";
        assertFalse(verify(true, "db.example.com", 22, serverKey, knownHosts));
    }

    @Test
    void rejectsAnotherKeyForAListedHostWhateverFailOnUnknownHost() throws Exception {
        String knownHosts = entry("10.0.0.12", otherKey) + entry("10.0.0.1", serverKey);
        assertFalse(verify(false, "10.0.0.1", 22, otherKey, knownHosts));
        assertFalse(verify(false, "db.example.com", 22, otherKey, entry("db.example.com", serverKey)));
    }

    @Test
    void appliesFailOnUnknownHostToHostsWithoutEntries() throws Exception {
        String knownHosts = entry("www.example.com", serverKey);
        assertFalse(verify(true, "db.example.com", 22, serverKey, knownHosts));
        assertTrue(verify(false, "db.example.com", 22, serverKey, knownHosts));
        assertFalse(verify(true, "db.example.com", 22, serverKey, ""));
        assertTrue(verify(false, "db.example.com", 22, serverKey, ""));
    }

    @Test
    void looksUpAnAddressAsConfigured() throws Exception {
        // an endpoint configured with an address is looked up by that address, not by the name of a reverse lookup
        String knownHosts = entry("localhost", otherKey) + entry("127.0.0.1", serverKey);
        assertFalse(verify(true, new InetSocketAddress("127.0.0.1", 22), otherKey, knownHosts));
        assertTrue(verify(true, new InetSocketAddress("127.0.0.1", 22), serverKey, knownHosts));
    }

    @Test
    void looksUpTheHostAsTheHostConfigurationOfTheConnectionWritesIt() throws Exception {
        // the address the client connected to writes ::1 as 0:0:0:0:0:0:0:1
        InetSocketAddress ipv6 = new InetSocketAddress("::1", 2222);
        String knownHosts = entry("[::1]:2222", serverKey);
        for (String hostName : new String[] { "::1", "[::1]" }) {
            // as an endpoint configured with ::1, or Host review-ipv6 / HostName ::1 in ~/.ssh/config, connects
            ClientSession session = session(new HostConfigEntry("review-ipv6", hostName, 2222, "smx"), null);
            for (boolean failOnUnknownHost : new boolean[] { true, false }) {
                assertTrue(verify(failOnUnknownHost, session, ipv6, serverKey, knownHosts), hostName);
                assertFalse(verify(failOnUnknownHost, session, ipv6, otherKey, knownHosts), hostName);
            }
        }
        // Host db / HostName db.example.com in ~/.ssh/config
        ClientSession db = session(new HostConfigEntry("db", "db.example.com", 22, "smx"), null);
        InetSocketAddress dbAddress
                = new InetSocketAddress(InetAddress.getByAddress("db.example.com", new byte[] { 10, 0, 0, 1 }), 22);
        assertTrue(verify(true, db, dbAddress, serverKey, entry("db.example.com", serverKey)));
        assertFalse(verify(true, db, dbAddress, serverKey, entry("db", serverKey)));
        assertFalse(verify(false, db, dbAddress, otherKey, entry("db.example.com", serverKey)));
        // without the host configuration of the connection, the connected address is used as it writes the host
        assertFalse(verify(true, session(null, null), ipv6, serverKey, knownHosts));
    }

    @Test
    void looksUpAHostReachedThroughAJumpHostAsItIsWritten() throws Exception {
        HostConfigEntry viaJump = new HostConfigEntry("review-ipv6", "::1", 2222, "smx", "jump.example.com");
        // the target, which SSHD connects to through the jump host and keeps as it is written
        ClientSession target = session(viaJump, new SshdSocketAddress("::1", 2222));
        InetSocketAddress ipv6 = new InetSocketAddress("::1", 2222);
        assertTrue(verify(true, target, ipv6, serverKey, entry("[::1]:2222", serverKey)));
        assertFalse(verify(false, target, ipv6, otherKey, entry("[::1]:2222", serverKey)));
        // the jump host itself is looked up by its own address, not by the name of the target
        ClientSession jump = session(viaJump, null);
        InetSocketAddress jumpAddress
                = new InetSocketAddress(InetAddress.getByAddress("jump.example.com", new byte[] { 10, 0, 0, 2 }), 22);
        String knownHosts = entry("jump.example.com", otherKey) + entry("[::1]:2222", serverKey);
        assertTrue(verify(true, jump, jumpAddress, otherKey, knownHosts));
        assertFalse(verify(true, jump, jumpAddress, serverKey, knownHosts));
    }

    @Test
    void verifiesAllSupportedKeyTypes() throws Exception {
        ResourceBasedSSHKeyVerifier verifier = new ResourceBasedSSHKeyVerifier(context, "classpath:known_hosts_types", true);
        try (InputStream knownHosts = getClass().getResourceAsStream("/known_hosts_types")) {
            assertNotNull(knownHosts);
            for (String line : IoUtils.readAllLines(knownHosts)) {
                String[] fields = line.split(" ");
                PublicKey key = PublicKeyEntry.parsePublicKeyEntry(fields[1] + " " + fields[2])
                        .resolvePublicKey(null, Collections.emptyMap(), PublicKeyEntryResolver.FAILING);
                assertTrue(verifier.verifyServerKey(null, new InetSocketAddress(fields[0], 22), key), fields[1]);
                assertFalse(verifier.verifyServerKey(null, new InetSocketAddress(fields[0], 22), serverKey), fields[1]);
            }
        }
    }

    private boolean verify(boolean failOnUnknownHost, String host, int port, PublicKey presented, String knownHosts)
            throws Exception {
        // the SSH client hands the verifier the configured host name together with its resolved address
        InetAddress address = InetAddress.getByAddress(host, new byte[] { 10, 0, 0, 1 });
        return verify(failOnUnknownHost, new InetSocketAddress(address, port), presented, knownHosts);
    }

    private boolean verify(
            boolean failOnUnknownHost, InetSocketAddress server, PublicKey presented, String knownHosts)
            throws Exception {
        return verify(failOnUnknownHost, null, server, presented, knownHosts);
    }

    private boolean verify(
            boolean failOnUnknownHost, ClientSession session, InetSocketAddress server, PublicKey presented,
            String knownHosts)
            throws Exception {
        Path file = Files.createTempFile(dir, "known_hosts", "");
        Files.writeString(file, knownHosts, StandardCharsets.UTF_8);
        ResourceBasedSSHKeyVerifier verifier = new ResourceBasedSSHKeyVerifier(context, "file:" + file, failOnUnknownHost);
        return verifier.verifyServerKey(session, server, presented);
    }

    /*
     * A session connected with the given host configuration, as the ssh producer and consumer connect, and reached
     * through a jump host when a target server is given: all that the verifier asks the session for.
     */
    private static ClientSession session(HostConfigEntry hostConfig, SshdSocketAddress targetServer) {
        AttributeRepository connectionContext = hostConfig != null
                ? AttributeRepository.ofKeyValuePair(ResourceBasedSSHKeyVerifier.HOST_CONFIG_ENTRY, hostConfig) : null;
        return (ClientSession) Proxy.newProxyInstance(ClientSession.class.getClassLoader(),
                new Class<?>[] { ClientSession.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getConnectionContext":
                            return connectionContext;
                        case "getAttribute":
                            return args[0] == ClientSessionCreator.TARGET_SERVER ? targetServer : null;
                        default:
                            Class<?> type = method.getReturnType();
                            // the default value of a primitive type, null otherwise
                            return type.isPrimitive() && type != void.class
                                    ? Array.get(Array.newInstance(type, 1), 0) : null;
                    }
                });
    }

    private static String base64(int length) {
        return Base64.getEncoder().encodeToString(new byte[length]);
    }

    private static String entry(String hosts, PublicKey key) {
        return hosts + " " + PublicKeyEntry.toString(key) + "\n";
    }

    private static String hashed(String host) throws Exception {
        byte[] salt = new byte[20];
        Arrays.fill(salt, (byte) 7);
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(salt, "HmacSHA1"));
        byte[] hash = mac.doFinal(host.getBytes(StandardCharsets.UTF_8));
        return "|1|" + Base64.getEncoder().encodeToString(salt) + "|" + Base64.getEncoder().encodeToString(hash);
    }
}
