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
package org.apache.camel.component.milo.call;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.AbstractMiloServerTest;
import org.apache.camel.component.milo.NodeIds;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfigBuilder;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;
import org.eclipse.milo.opcua.stack.server.security.ServerCertificateValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.milo.NodeIds.nodeValue;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for calling from the client side
 */
public class CallClientTest extends AbstractMiloServerTest {

    private static final String DIRECT_START_1 = "direct:start1";

    private static final String MILO_CLIENT_BASE_C1 = "milo-client:opc.tcp://localhost:@@port@@";

    private static final String MILO_CLIENT_ITEM_C1_1
            = MILO_CLIENT_BASE_C1 + "?node=" + NodeIds.nodeValue(MockCamelNamespace.URI, MockCamelNamespace.FOLDER_ID)
              + "&method=" + nodeValue(MockCamelNamespace.URI, MockCamelNamespace.CALL_ID) + "&overrideHost=true";

    private static final Logger LOG = LoggerFactory.getLogger(CallClientTest.class);

    private OpcUaServer server;
    private MockCamelNamespace namespace;
    private MockCallMethod callMethod;

    @Produce(DIRECT_START_1)
    private ProducerTemplate producer1;

    @BeforeEach
    public void setup(TestInfo testInfo) {
        final var displayName = testInfo.getDisplayName();
        LOG.info("********************************************************************************");
        LOG.info(displayName);
        LOG.info("********************************************************************************");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_START_1).to(resolve(MILO_CLIENT_ITEM_C1_1));
            }
        };
    }

    @Override
    protected boolean isAddServer() {
        return false;
    }

    @BeforeEach
    public void start() throws Exception {
        final OpcUaServerConfigBuilder cfg = OpcUaServerConfig.builder();

        cfg.setCertificateManager(new DefaultCertificateManager());
        cfg.setEndpoints(createEndpointConfigurations(Arrays.asList(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS),
                EnumSet.of(SecurityPolicy.None)));
        cfg.setApplicationName(LocalizedText.english("Apache Camel Milo Server"));
        cfg.setApplicationUri("urn:mock:namespace");
        cfg.setProductUri("urn:org:apache:camel:milo");
        cfg.setCertificateManager(new DefaultCertificateManager());
        cfg.setCertificateValidator(new InsecureCertificateValidator());

        this.server = new OpcUaServer(cfg.build());

        this.namespace = new MockCamelNamespace(this.server, node -> callMethod = new MockCallMethod(node));
        this.namespace.startup();
        this.server.startup().get();
    }

    private Set<EndpointConfiguration> createEndpointConfigurations(
            List<UserTokenPolicy> userTokenPolicies, Set<SecurityPolicy> securityPolicies) {
        Set<EndpointConfiguration> endpointConfigurations = new LinkedHashSet<>();

        String bindAddress = "0.0.0.0";
        Set<String> hostnames = new LinkedHashSet<>();
        hostnames.add(HostnameUtil.getHostname());
        hostnames.addAll(HostnameUtil.getHostnames(bindAddress));

        UserTokenPolicy[] tokenPolicies = new UserTokenPolicy[] { USER_TOKEN_POLICY_ANONYMOUS };

        for (String hostname : hostnames) {
            EndpointConfiguration.Builder builder = EndpointConfiguration.newBuilder()
                    .setBindAddress(bindAddress)
                    .setHostname(hostname)
                    .setCertificate(() -> null)
                    .addTokenPolicies(tokenPolicies);

            if (securityPolicies == null || securityPolicies.contains(SecurityPolicy.None)) {
                EndpointConfiguration.Builder noSecurityBuilder = builder.copy()
                        .setSecurityPolicy(SecurityPolicy.None)
                        .setSecurityMode(MessageSecurityMode.None);

                endpointConfigurations.add(buildTcpEndpoint(noSecurityBuilder));
            }
            /*
             * It's good practice to provide a discovery-specific endpoint with no security.
             * It's required practice if all regular endpoints have security configured.
             *
             * Usage of the  "/discovery" suffix is defined by OPC UA Part 6:
             *
             * Each OPC UA Server Application implements the Discovery Service Set. If the OPC UA Server requires a
             * different address for this Endpoint it shall create the address by appending the path "/discovery" to
             * its base address.
             */

            EndpointConfiguration.Builder discoveryBuilder = builder.copy()
                    .setPath("/discovery")
                    .setSecurityPolicy(SecurityPolicy.None)
                    .setSecurityMode(MessageSecurityMode.None);

            endpointConfigurations.add(buildTcpEndpoint(discoveryBuilder));
        }

        return endpointConfigurations;
    }

    private EndpointConfiguration buildTcpEndpoint(EndpointConfiguration.Builder base) {
        return base.copy()
                .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
                .setBindPort(getServerPort())
                .build();
    }

    @Test
    public void testCall1() {
        // call

        doCall(this.producer1, "foo");
        doCall(this.producer1, "bar");

        // assert
        assertNotNull(this.callMethod);
        assertArrayEquals(new Object[] { "out-foo", "out-bar" }, this.callMethod.getCalls().toArray());
    }

    private static void doCall(final ProducerTemplate producerTemplate, final Object input) {
        // we always write synchronously since we do need the message order
        producerTemplate.sendBodyAndHeader(input, "await", true);
    }

    private static final class InsecureCertificateValidator implements ServerCertificateValidator {

        public static final ServerCertificateValidator INSTANCE = new CallClientTest.InsecureCertificateValidator();

        private InsecureCertificateValidator() {
        }

        @Override
        public void validateCertificateChain(List<X509Certificate> list, String s) throws UaException {
        }

        @Override
        public void validateCertificateChain(List<X509Certificate> list) throws UaException {
        }
    }
}
