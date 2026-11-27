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
package org.apache.camel.component.milo;

import java.security.cert.X509Certificate;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.call.TestCertificateFactory;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultApplicationGroup;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateStore;
import org.eclipse.milo.opcua.stack.core.security.MemoryTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The milo-server component validates the certificates of the clients with its certificateValidator option.
 */
class ServerClientCertificateValidatorTest extends AbstractMiloServerTest {

    private static final String DIRECT_START = "direct:start";

    private static final String MILO_SERVER_ITEM = "milo-server:myitem1";

    // the client trusts the server: the validation of the client certificate by the server is under test here
    private static final String MILO_CLIENT = "milo-client:opc.tcp://foo:bar@localhost:@@port@@?node="
                                              + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "myitem1")
                                              + "&keyStoreUrl=file:src/test/resources/keystore&keyStorePassword=testtest"
                                              + "&keyPassword=test&keyAlias=test&allowedSecurityPolicies=Basic256Sha256"
                                              + "&trustAllCertificates=true"
                                              + "&discoveryEndpointSuffix=/discovery&overrideHost=true";

    private final SwitchableCertificateValidator certificateValidator = new SwitchableCertificateValidator();

    @EndpointInject("mock:accepted")
    MockEndpoint acceptedEndpoint;

    @EndpointInject("mock:rejected")
    MockEndpoint rejectedEndpoint;

    @Produce(DIRECT_START)
    ProducerTemplate producer;

    @Override
    protected void configureMiloServer(final MiloServerComponent server) throws Exception {
        super.configureMiloServer(server);

        // the certificate group of the server accepts any client, so only the custom validator can reject one
        final DefaultApplicationGroup certificateGroup = DefaultApplicationGroup.createAndInitialize(
                new MemoryTrustListManager(), new MemoryCertificateStore(), new TestCertificateFactory(),
                new CertificateValidator.InsecureCertificateValidator());

        server.setApplicationUri("urn:eclipse:milo:test");
        server.setCertificateManager(new DefaultCertificateManager(new MemoryCertificateQuarantine(), certificateGroup));
        server.setCertificate(certificateGroup.getCertificateEntries().get(0).certificateChain[0]);
        server.setCertificateValidator(this.certificateValidator);
        server.setSecurityPolicies(EnumSet.of(SecurityPolicy.Basic256Sha256));
        server.setUsernameSecurityPolicyUri(SecurityPolicy.Basic256Sha256);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_START).to(MILO_SERVER_ITEM);

                // the test starts the consumers, each with its own connection
                from(resolve(MILO_CLIENT + "&clientId=accepted")).routeId("accepted").autoStartup(false)
                        .to("mock:accepted");
                from(resolve(MILO_CLIENT + "&clientId=rejected")).routeId("rejected").autoStartup(false)
                        .to("mock:rejected");
            }
        };
    }

    @Test
    void serverValidatesClientCertificatesWithTheCustomValidator() throws Exception {
        final X509Certificate clientCertificate = loadDefaultTestKey().getCertificate();

        // the value is written before the consumers start, so it is the first notification of a client that connects
        this.producer.sendBody("Foo");

        // the validator accepts the client
        this.acceptedEndpoint.setMinimumExpectedMessageCount(1);
        testBody(this.acceptedEndpoint.message(0), assertGoodValue("Foo"));
        context.getRouteController().startRoute("accepted");
        this.acceptedEndpoint.assertIsSatisfied();

        assertFalse(this.certificateValidator.validated.isEmpty(), "The custom certificate validator was not used");
        assertEquals(clientCertificate, this.certificateValidator.validated.get(0).get(0));

        // the validator rejects the client: it cannot connect, and reports its node without communication
        this.certificateValidator.accept.set(false);
        final int validationsBefore = this.certificateValidator.validated.size();
        this.rejectedEndpoint.setExpectedCount(1);
        testBody(this.rejectedEndpoint.message(0), assertNoCommunication());
        context.getRouteController().startRoute("rejected");
        this.rejectedEndpoint.assertIsSatisfied();

        assertTrue(this.certificateValidator.validated.size() > validationsBefore,
                "The custom certificate validator was not used for the rejected client");
    }

    private static final class SwitchableCertificateValidator implements CertificateValidator {

        private final AtomicBoolean accept = new AtomicBoolean(true);
        private final List<List<X509Certificate>> validated = new CopyOnWriteArrayList<>();

        @Override
        public void validateCertificateChain(
                final List<X509Certificate> certificateChain, final String applicationUri, final String[] validHostNames)
                throws UaException {
            this.validated.add(List.copyOf(certificateChain));
            if (!this.accept.get()) {
                throw new UaException(StatusCodes.Bad_SecurityChecksFailed);
            }
        }
    }
}
