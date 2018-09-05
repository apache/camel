/**
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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.AbstractMiloServerTest;
import org.apache.camel.component.milo.call.MockCall.Call1;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfigBuilder;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.stack.core.application.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.application.InsecureCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.milo.NodeIds.nodeValue;

/**
 * Unit tests for calling from the client side
 */
public class CallClientTest extends AbstractMiloServerTest {

    private static final String DIRECT_START_1 = "direct:start1";

    private static final String MILO_CLIENT_BASE_C1 = "milo-client:tcp://localhost:@@port@@";

    private static final String MILO_CLIENT_ITEM_C1_1 = MILO_CLIENT_BASE_C1 + "?node=" + nodeValue(MockNamespace.URI, MockNamespace.FOLDER_ID) + "&method="
                                                        + nodeValue(MockNamespace.URI, "id1") + "&overrideHost=true";

    @Produce(uri = DIRECT_START_1)
    protected ProducerTemplate producer1;

    private OpcUaServer server;

    private Call1 call1;

    @Override
    protected boolean isAddServer() {
        return false;
    }

    @Before
    public void start() throws Exception {
        final OpcUaServerConfigBuilder config = new OpcUaServerConfigBuilder();
        config.setBindAddresses(Arrays.asList("localhost"));
        config.setBindPort(getServerPort());
        config.setIdentityValidator(AnonymousIdentityValidator.INSTANCE);
        config.setUserTokenPolicies(Arrays.asList(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS));
        config.setSecurityPolicies(EnumSet.of(SecurityPolicy.None));
        config.setCertificateManager(new DefaultCertificateManager());
        config.setCertificateValidator(new InsecureCertificateValidator());

        this.server = new OpcUaServer(config.build());

        this.call1 = new MockCall.Call1();

        this.server.getNamespaceManager().registerAndAdd(MockNamespace.URI, index -> {

            final List<UaMethodNode> methods = new LinkedList<>();
            methods.add(MockCall.fromNode(index, this.server.getNodeMap(), "id1", "name1", this.call1));

            return new MockNamespace(index, this.server, methods);
        });

        this.server.startup().get();
    }

    @After
    public void stop() {
        this.server.shutdown();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(DIRECT_START_1).to(resolve(MILO_CLIENT_ITEM_C1_1));
            }
        };
    }

    @Test
    public void testCall1() throws Exception {
        // call

        doCall(this.producer1, "foo");
        doCall(this.producer1, "bar");

        // assert

        Assert.assertArrayEquals(new Object[] {"foo", "bar"}, this.call1.calls.toArray());
    }

    private static void doCall(final ProducerTemplate producerTemplate, final Object input) {
        // we always write synchronously since we do need the message order
        producerTemplate.sendBodyAndHeader(input, "await", true);
    }
}
