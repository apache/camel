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
package org.apache.camel.component.milo.server;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Set;

import org.apache.camel.component.milo.KeyStoreLoader;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.eclipse.milo.opcua.sdk.server.EndpointConfig;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test setting security policies
 */
public class ServerSetSecurityPoliciesTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ServerSetSecurityPoliciesTest.class);

    @BeforeEach
    public void setup(TestInfo testInfo) {
        final var displayName = testInfo.getDisplayName();
        LOG.info("********************************************************************************");
        LOG.info(displayName);
        LOG.info("********************************************************************************");
    }

    @Test
    public void testSetSecurityPolicies1() throws IOException {
        try (final MiloServerComponent component = new MiloServerComponent()) {
            Assertions.assertDoesNotThrow(() -> component.setSecurityPoliciesById("None"));
        }
    }

    @Test
    public void testSetSecurityPolicies2() throws IOException {
        try (final MiloServerComponent component = new MiloServerComponent()) {
            Assertions.assertDoesNotThrow(
                    () -> component.setSecurityPoliciesById("http://opcfoundation.org/UA/SecurityPolicy#Basic256Sha256"));
        }
    }

    @Test
    public void testSetSecurityPolicies3() throws IOException {
        try (final MiloServerComponent component = new MiloServerComponent()) {
            Assertions.assertDoesNotThrow(
                    () -> component.setSecurityPoliciesById("None,http://opcfoundation.org/UA/SecurityPolicy#Basic256Sha256"));
        }
    }

    @Test
    public void testBasic256Sha256HasBothATcpAndAnHttpsEndpoint() throws Exception {
        try (final MiloServerComponent component = new MiloServerComponent()) {
            // a secured policy cannot be built without one: EndpointConfig.Builder.build() rejects it
            component.setCertificate(loadTestCertificate());

            final Set<EndpointConfig> endpoints
                    = component.createEndpointConfigs(null, Set.of(SecurityPolicy.Basic256Sha256));

            Assertions.assertTrue(
                    hasEndpoint(endpoints, TransportProfile.TCP_UASC_UABINARY, SecurityPolicy.Basic256Sha256,
                            MessageSecurityMode.SignAndEncrypt),
                    "expected a TCP endpoint with Basic256Sha256 / SignAndEncrypt");
            Assertions.assertTrue(
                    hasEndpoint(endpoints, TransportProfile.HTTPS_UABINARY, SecurityPolicy.Basic256Sha256,
                            MessageSecurityMode.Sign),
                    "expected an HTTPS endpoint with Basic256Sha256 / Sign");
        }
    }

    @Test
    public void testNoneAndBasic256Sha256HaveEndpointsForBothPolicies() throws Exception {
        try (final MiloServerComponent component = new MiloServerComponent()) {
            component.setCertificate(loadTestCertificate());

            final Set<EndpointConfig> endpoints
                    = component.createEndpointConfigs(null, Set.of(SecurityPolicy.None, SecurityPolicy.Basic256Sha256));

            Assertions.assertTrue(
                    hasEndpoint(endpoints, TransportProfile.TCP_UASC_UABINARY, SecurityPolicy.None,
                            MessageSecurityMode.None),
                    "expected a TCP endpoint with no security");
            Assertions.assertTrue(
                    hasEndpoint(endpoints, TransportProfile.HTTPS_UABINARY, SecurityPolicy.None,
                            MessageSecurityMode.None),
                    "expected an HTTPS endpoint with no security");
            Assertions.assertTrue(
                    hasEndpoint(endpoints, TransportProfile.TCP_UASC_UABINARY, SecurityPolicy.Basic256Sha256,
                            MessageSecurityMode.SignAndEncrypt),
                    "expected a TCP endpoint with Basic256Sha256 / SignAndEncrypt");
            Assertions.assertTrue(
                    hasEndpoint(endpoints, TransportProfile.HTTPS_UABINARY, SecurityPolicy.Basic256Sha256,
                            MessageSecurityMode.Sign),
                    "expected an HTTPS endpoint with Basic256Sha256 / Sign");
        }
    }

    private static X509Certificate loadTestCertificate() throws Exception {
        final KeyStoreLoader loader = new KeyStoreLoader();
        loader.setUrl("file:src/test/resources/keystore");
        loader.setKeyStorePassword("testtest");
        loader.setKeyPassword("test");
        return loader.load().getCertificate();
    }

    /**
     * Whether one of the regular (non-discovery) endpoints uses the given transport, policy and security mode. The
     * discovery endpoints are skipped: they always carry no security, whatever the configured policies are.
     */
    private static boolean hasEndpoint(
            Set<EndpointConfig> endpoints, TransportProfile transportProfile, SecurityPolicy securityPolicy,
            MessageSecurityMode securityMode) {
        return endpoints.stream()
                .filter(endpoint -> !"/discovery".equals(endpoint.getPath()))
                .anyMatch(endpoint -> endpoint.getTransportProfile() == transportProfile
                        && endpoint.getSecurityPolicy() == securityPolicy
                        && endpoint.getSecurityMode() == securityMode);
    }

}
