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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.call.TestCertificateFactory;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultApplicationGroup;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.DefaultClientCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateStore;
import org.eclipse.milo.opcua.stack.core.security.MemoryTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client validates the certificate of a server that only offers a secured endpoint.
 */
class ServerCertificateValidationTest extends AbstractMiloServerTest {

    private static final String DIRECT_START = "direct:start";

    private static final String MILO_SERVER_ITEM = "milo-server:myitem1";

    private static final String MILO_CLIENT = "milo-client:opc.tcp://foo:bar@localhost:@@port@@?node="
                                              + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "myitem1")
                                              + "&keyStoreUrl=file:src/test/resources/keystore&keyStorePassword=testtest"
                                              + "&keyPassword=test&keyAlias=test&allowedSecurityPolicies=Basic256Sha256"
                                              + "&discoveryEndpointSuffix=/discovery&overrideHost=true";

    private static final String SERVER_APPLICATION_URI = "urn:eclipse:milo:test";

    @TempDir
    static Path pki;

    private final RecordingCertificateValidator recordingValidator = new RecordingCertificateValidator();

    @EndpointInject("mock:trustList")
    MockEndpoint trustListEndpoint;

    @EndpointInject("mock:emptyTrustList")
    MockEndpoint emptyTrustListEndpoint;

    @EndpointInject("mock:notConfigured")
    MockEndpoint notConfiguredEndpoint;

    @EndpointInject("mock:trustAll")
    MockEndpoint trustAllEndpoint;

    @EndpointInject("mock:certificateValidator")
    MockEndpoint certificateValidatorEndpoint;

    @Produce(DIRECT_START)
    ProducerTemplate producer;

    @Override
    protected void configureContext(final CamelContext context) throws Exception {
        super.configureContext(context);
        context.getRegistry().bind("recordingValidator", this.recordingValidator);
    }

    @Override
    protected void configureMiloServer(final MiloServerComponent server) throws Exception {
        super.configureMiloServer(server);

        // the server uses a self-signed certificate, and accepts any client: the client is under test here
        final DefaultApplicationGroup certificateGroup = DefaultApplicationGroup.createAndInitialize(
                new MemoryTrustListManager(), new MemoryCertificateStore(), new TestCertificateFactory(),
                new CertificateValidator.InsecureCertificateValidator());
        final X509Certificate serverCertificate = certificateGroup.getCertificateEntries().get(0).certificateChain[0];

        server.setApplicationUri(SERVER_APPLICATION_URI);
        server.setCertificateManager(new DefaultCertificateManager(new MemoryCertificateQuarantine(), certificateGroup));
        server.setCertificate(serverCertificate);
        server.setSecurityPolicies(EnumSet.of(SecurityPolicy.Basic256Sha256));
        server.setUsernameSecurityPolicyUri(SecurityPolicy.Basic256Sha256);

        final Path trustedCerts = Files.createDirectories(pki.resolve("trusted").resolve("trusted").resolve("certs"));
        Files.write(trustedCerts.resolve("server.der"), serverCertificate.getEncoded());

        this.recordingValidator.trust(serverCertificate);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_START).to(MILO_SERVER_ITEM);

                // consumers deliver the current server value when they subscribe, so they are started
                // only after the initial write to avoid receiving both the initial value and the write as two messages
                from(resolve(trustListClient("trusted"))).autoStartup(false).to("mock:trustList");
                from(resolve(trustListClient("empty"))).autoStartup(false).to("mock:emptyTrustList");
                from(resolve(MILO_CLIENT)).autoStartup(false).to("mock:notConfigured");
                from(resolve(MILO_CLIENT + "&trustAllCertificates=true")).autoStartup(false).to("mock:trustAll");
                from(resolve(MILO_CLIENT + "&certificateValidator=#recordingValidator")).autoStartup(false)
                        .to("mock:certificateValidator");
            }
        };
    }

    @Test
    void onlyClientsThatCanValidateTheServerCertificateConnect() throws Exception {
        this.trustListEndpoint.setExpectedCount(1);
        this.trustAllEndpoint.setExpectedCount(1);
        this.certificateValidatorEndpoint.setExpectedCount(1);

        // the clients that cannot connect report their node without communication, and nothing else
        this.emptyTrustListEndpoint.setExpectedCount(1);
        testBody(this.emptyTrustListEndpoint.message(0), assertNoCommunication());
        this.emptyTrustListEndpoint.setAssertPeriod(5_000);
        this.notConfiguredEndpoint.setExpectedCount(1);
        testBody(this.notConfiguredEndpoint.message(0), assertNoCommunication());
        this.notConfiguredEndpoint.setAssertPeriod(5_000);

        // write the initial value before starting the consumer routes so that each consumer receives
        // exactly one notification (the current value at subscription time) rather than two
        // (the uninitialized value at subscription time, then the write)
        this.producer.sendBody("Foo");
        context.getRouteController().startAllRoutes();

        MockEndpoint.assertIsSatisfied(context);

        // the server certificate rejected by the empty trust list is kept for review
        final Path rejectedCerts = pki.resolve("empty").resolve("rejected").resolve("certs");
        await().atMost(10, TimeUnit.SECONDS).until(() -> countFiles(rejectedCerts) > 0);

        assertTrue(this.recordingValidator.validations.get() > 0, "The configured certificate validator was not used");

        // a producer of a client that cannot connect gets the reason of the last connection attempt
        assertConnectionFailure(MILO_CLIENT, "The certificate of the OPC UA server cannot be validated");
        assertConnectionFailure(trustListClient("empty"), "Bad_SecurityChecksFailed");
    }

    private void assertConnectionFailure(final String clientUri, final String expectedReason) {
        final Exchange exchange = this.producer.send(resolve(clientUri + "&defaultAwaitWrites=true"),
                e -> e.getMessage().setBody("Bar"));

        final StringBuilder causes = new StringBuilder();
        for (Throwable cause = exchange.getException(); cause != null; cause = cause.getCause()) {
            causes.append(cause).append('\n');
        }
        assertTrue(causes.indexOf("No connected") >= 0, causes::toString);
        assertTrue(causes.indexOf(expectedReason) >= 0, causes::toString);
    }

    private static String trustListClient(final String directory) {
        return MILO_CLIENT + "&trustListDirectory=RAW(" + pki.resolve(directory) + ")";
    }

    private static long countFiles(final Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.count();
        }
    }

    private static final class RecordingCertificateValidator implements CertificateValidator {

        private final AtomicInteger validations = new AtomicInteger();
        private final MemoryTrustListManager trustList = new MemoryTrustListManager();
        private final CertificateValidator delegate
                = new DefaultClientCertificateValidator(this.trustList, new MemoryCertificateQuarantine());

        void trust(final X509Certificate certificate) {
            this.trustList.addTrustedCertificate(certificate);
        }

        @Override
        public void validateCertificateChain(
                final List<X509Certificate> certificateChain, final String applicationUri, final String[] validHostNames)
                throws UaException {
            this.validations.incrementAndGet();
            this.delegate.validateCertificateChain(certificateChain, applicationUri, validHostNames);
        }
    }
}
