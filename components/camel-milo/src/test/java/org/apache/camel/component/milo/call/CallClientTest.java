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

import static org.apache.camel.component.milo.NodeIds.nodeValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import org.eclipse.milo.opcua.sdk.server.EndpointConfig;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfigBuilder;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.security.*;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransport;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransportConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for calling from the client side
 */
public class CallClientTest extends AbstractMiloServerTest {

    private static final String DIRECT_START_1 = "direct:start1";

    private static final String MILO_CLIENT_BASE_C1 = "milo-client:opc.tcp://localhost:@@port@@";

    private static final String MILO_CLIENT_ITEM_C1_1 =
            MILO_CLIENT_BASE_C1 + "?node=" + NodeIds.nodeValue(MockCamelNamespace.URI, MockCamelNamespace.FOLDER_ID)
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

        //        cfg.setCertificateManager(new DefaultCertificateManager()); // TODO setCertificateManager is called
        // afterwards
        cfg.setEndpoints(createEndpointConfigs(
                Arrays.asList(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS), EnumSet.of(SecurityPolicy.None)));
        cfg.setApplicationName(LocalizedText.english("Apache Camel Milo Server"));
        cfg.setApplicationUri("urn:mock:namespace");
        cfg.setProductUri("urn:org:apache:camel:milo");
        // FIXME migrate to new certifcate
        //        cfg.setCertificateManager(new DefaultCertificateManager());
        ////        cfg.setCertificateValidator(new InsecureCertificateValidator());
        //        cfg.setCertificateValidator(new CertificateValidator.InsecureCertificateValidator());

        var certificateQuarantine = new MemoryCertificateQuarantine();
        var trustListManager = new MemoryTrustListManager();
        var certificateStore = new MemoryCertificateStore();
        var certificateFactory = new TestCertificateFactory();
        var certificateGroup = new DefaultApplicationGroup(
                trustListManager,
                certificateStore,
                certificateFactory,
                new CertificateValidator.InsecureCertificateValidator());
        cfg.setCertificateManager(new DefaultCertificateManager(certificateQuarantine, certificateGroup));

        // https://github.com/eclipse-milo/milo/blob/1.0/milo-examples/server-examples/src/main/java/org/eclipse/milo/examples/server/ExampleServer.java
        //        this.server = new OpcUaServer(cfg.build());
        OpcUaServerConfig serverConfig = cfg.build();
        this.server = new OpcUaServer(serverConfig, transportProfile -> {
            assert transportProfile == TransportProfile.TCP_UASC_UABINARY;

            OpcTcpServerTransportConfig transportConfig =
                    OpcTcpServerTransportConfig.newBuilder().build();

            return new OpcTcpServerTransport(transportConfig);
        });

        this.namespace = new MockCamelNamespace(this.server, node -> callMethod = new MockCallMethod(node));
        this.namespace.startup();
        this.server.startup().get();
    }

    private Set<EndpointConfig> createEndpointConfigs(
            List<UserTokenPolicy> userTokenPolicies, Set<SecurityPolicy> securityPolicies) {
        Set<EndpointConfig> endpointConfigs = new LinkedHashSet<>();

        String bindAddress = "0.0.0.0";
        Set<String> hostnames = new LinkedHashSet<>();
        hostnames.add(HostnameUtil.getHostname());
        hostnames.addAll(HostnameUtil.getHostnames(bindAddress));

        UserTokenPolicy[] tokenPolicies = new UserTokenPolicy[] {OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS};

        for (String hostname : hostnames) {
            EndpointConfig.Builder builder = EndpointConfig.newBuilder()
                    .setBindAddress(bindAddress)
                    .setHostname(hostname)
                    .setCertificate(() -> null)
                    .addTokenPolicies(tokenPolicies);

            if (securityPolicies == null || securityPolicies.contains(SecurityPolicy.None)) {
                EndpointConfig.Builder noSecurityBuilder =
                        builder.copy().setSecurityPolicy(SecurityPolicy.None).setSecurityMode(MessageSecurityMode.None);

                endpointConfigs.add(buildTcpEndpoint(noSecurityBuilder));
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

            EndpointConfig.Builder discoveryBuilder = builder.copy()
                    .setPath("/discovery")
                    .setSecurityPolicy(SecurityPolicy.None)
                    .setSecurityMode(MessageSecurityMode.None);

            endpointConfigs.add(buildTcpEndpoint(discoveryBuilder));
        }

        return endpointConfigs;
    }

    private EndpointConfig buildTcpEndpoint(EndpointConfig.Builder base) {
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
        assertArrayEquals(
                new Object[] {"out-foo", "out-bar"}, this.callMethod.getCalls().toArray());
    }

    private static void doCall(final ProducerTemplate producerTemplate, final Object input) {
        // we always write synchronously since we do need the message order
        producerTemplate.sendBodyAndHeader(input, "await", true);
    }
}
