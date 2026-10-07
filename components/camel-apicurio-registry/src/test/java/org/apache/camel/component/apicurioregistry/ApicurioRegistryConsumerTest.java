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
package org.apache.camel.component.apicurioregistry;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.VersionState;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApicurioRegistryConsumerTest {

    private static final String ENDPOINT_URI
            = "apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&delay=100";

    private final RegistryClient mockClient = mock(RegistryClient.class, RETURNS_DEEP_STUBS);
    private CamelContext context;
    private FakeVersionPages pages;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        ApicurioRegistryComponent component = new ApicurioRegistryComponent(context);
        component.getConfiguration().setRegistryUrl("http://localhost:8080/apis/registry/v3");
        context.addComponent("apicurio-registry", component);
        pages = new FakeVersionPages(
                mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact").versions());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (context != null) {
            context.stop();
        }
    }

    private ApicurioRegistryConsumer createConsumer(String uri, List<Exchange> received) throws Exception {
        ApicurioRegistryEndpoint endpoint = context.getEndpoint(uri, ApicurioRegistryEndpoint.class);
        endpoint.setRegistryClient(mockClient);
        return (ApicurioRegistryConsumer) endpoint.createConsumer(received::add);
    }

    private static List<Long> globalIds(List<Exchange> exchanges) {
        return exchanges.stream()
                .map(e -> e.getIn().getHeader(ApicurioRegistryConstants.HEADER_GLOBAL_ID, Long.class))
                .toList();
    }

    @Test
    void testPollNewVersionsInRoute() throws Exception {
        pages.add(1, 2);
        ApicurioRegistryEndpoint endpoint = context.getEndpoint(ENDPOINT_URI, ApicurioRegistryEndpoint.class);
        endpoint.setRegistryClient(mockClient);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(ENDPOINT_URI).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedHeaderValuesReceivedInAnyOrder(ApicurioRegistryConstants.HEADER_GLOBAL_ID, 1L, 2L);
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Test
    void testPollOutOfOrderGlobalIds() throws Exception {
        pages.add(5, 2, 8);
        List<Exchange> received = new ArrayList<>();
        ApicurioRegistryConsumer consumer = createConsumer(ENDPOINT_URI, received);

        assertThat(consumer.poll()).isEqualTo(3);
        assertThat(globalIds(received)).containsExactly(2L, 5L, 8L);
    }

    @Test
    void testPollOnlyNewVersionsAfterInitial() throws Exception {
        pages.add(1);
        List<Exchange> received = new ArrayList<>();
        ApicurioRegistryConsumer consumer = createConsumer(ENDPOINT_URI, received);
        assertThat(consumer.poll()).isEqualTo(1);
        assertThat(consumer.poll()).isZero();

        pages.add(2);
        assertThat(consumer.poll()).isEqualTo(1);
        assertThat(consumer.poll()).isZero();
        assertThat(globalIds(received)).containsExactly(1L, 2L);
    }

    @Test
    void testPollPagesThroughManyVersionsAndStopsAtWatermark() throws Exception {
        pages.addRange(1, 250);
        List<Exchange> received = new ArrayList<>();
        ApicurioRegistryConsumer consumer = createConsumer(ENDPOINT_URI, received);

        // more than the registry's default page of 20 versions, and more than one consumer page
        assertThat(consumer.poll()).isEqualTo(250);
        assertThat(globalIds(received)).containsExactlyElementsOf(LongStream.rangeClosed(1, 250).boxed().toList());
        assertThat(pages.requestedOffsets).containsExactly(0, 100, 200);

        // an idle poll only probes the newest version
        pages.requestedOffsets.clear();
        pages.requestedLimits.clear();
        assertThat(consumer.poll()).isZero();
        assertThat(pages.requestedLimits).containsExactly(1);

        pages.requestedOffsets.clear();
        pages.requestedLimits.clear();
        pages.add(251);
        assertThat(consumer.poll()).isEqualTo(1);
        assertThat(globalIds(received)).endsWith(251L);
        // the probe finds a new version, then the first page reaches the watermark, so older pages are not fetched
        assertThat(pages.requestedOffsets).containsExactly(0, 0);
        assertThat(pages.requestedLimits).containsExactly(1, ApicurioRegistryConsumer.PAGE_SIZE);
    }

    @Test
    void testFetchContentClosesStream() throws Exception {
        pages.add(1);
        byte[] content = "{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream stream = spy(new ByteArrayInputStream(content));
        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().byVersionExpression("1").content().get()).thenReturn(stream);
        List<Exchange> received = new ArrayList<>();
        ApicurioRegistryConsumer consumer = createConsumer(ENDPOINT_URI + "&fetchContent=true", received);

        assertThat(consumer.poll()).isEqualTo(1);
        assertThat(received.get(0).getIn().getBody()).isEqualTo(content);
        verify(stream).close();
    }

    @ParameterizedTest
    @ValueSource(strings = { "testGroup", "testGroup/", "/someArtifact" })
    void testMissingIdentifierFailsWhenCreatingConsumer(String path) throws Exception {
        ApicurioRegistryEndpoint endpoint = context.getEndpoint(
                "apicurio-registry:" + path + "?registryUrl=http://localhost:8080/apis/registry/v3",
                ApicurioRegistryEndpoint.class);
        assertThatThrownBy(() -> endpoint.createConsumer(exchange -> {
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Both groupId and artifactId are required for the consumer");
    }

    @Test
    void testFetchContentSkipsDisabledAndMissingVersions() throws Exception {
        SearchedVersion disabled = FakeVersionPages.version(2);
        disabled.setState(VersionState.DISABLED);
        pages.add(1).add(disabled).add(3, 4);
        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().byVersionExpression("1").content().get())
                .thenReturn(new ByteArrayInputStream(new byte[] { 1 }));
        // a version disabled or deleted after the list was read: the registry answers 404 for its content
        ProblemDetails notFound = new ProblemDetails();
        notFound.setStatus(404);
        notFound.setName("VersionNotFoundException");
        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().byVersionExpression("3").content().get()).thenThrow(notFound);
        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().byVersionExpression("4").content().get())
                .thenReturn(new ByteArrayInputStream(new byte[] { 4 }));
        List<Exchange> received = new ArrayList<>();
        ApicurioRegistryConsumer consumer = createConsumer(ENDPOINT_URI + "&fetchContent=true", received);

        assertThat(consumer.poll()).isEqualTo(2);
        assertThat(globalIds(received)).containsExactly(1L, 4L);
        verify(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().byVersionExpression("2").content(), never()).get();
        assertThat(consumer.poll()).isZero();
    }

    @Test
    void testOtherContentErrorsAreRetried() throws Exception {
        pages.add(1);
        ProblemDetails unavailable = new ProblemDetails();
        unavailable.setStatus(503);
        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().byVersionExpression("1").content().get())
                .thenThrow(unavailable).thenReturn(new ByteArrayInputStream(new byte[] { 1 }));
        List<Exchange> received = new ArrayList<>();
        ApicurioRegistryConsumer consumer = createConsumer(ENDPOINT_URI + "&fetchContent=true", received);

        assertThatThrownBy(consumer::poll).isSameAs(unavailable);
        assertThat(consumer.poll()).isEqualTo(1);
        assertThat(globalIds(received)).containsExactly(1L);
    }

    @Test
    void testHandledFailureAdvancesPastVersion() throws Exception {
        pages.add(1, 2);
        AtomicInteger attemptsForFirstVersion = new AtomicInteger();
        ApicurioRegistryEndpoint endpoint = context.getEndpoint(ENDPOINT_URI, ApicurioRegistryEndpoint.class);
        endpoint.setRegistryClient(mockClient);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).handled(true).to("mock:skipped");
                from(ENDPOINT_URI)
                        .process(exchange -> {
                            if (exchange.getIn().getHeader(ApicurioRegistryConstants.HEADER_GLOBAL_ID, Long.class) == 1L) {
                                attemptsForFirstVersion.incrementAndGet();
                                throw new IllegalStateException("always fails");
                            }
                        })
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedHeaderValuesReceivedInAnyOrder(ApicurioRegistryConstants.HEADER_GLOBAL_ID, 2L);
        MockEndpoint skipped = context.getEndpoint("mock:skipped", MockEndpoint.class);
        skipped.expectedHeaderValuesReceivedInAnyOrder(ApicurioRegistryConstants.HEADER_GLOBAL_ID, 1L);
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        assertThat(attemptsForFirstVersion.get()).isEqualTo(1);
    }

    @Test
    void testRouteFailureHandledByErrorHandlerIsRetried() throws Exception {
        pages.add(1, 2);
        AtomicInteger attemptsForFirstVersion = new AtomicInteger();
        ApicurioRegistryEndpoint endpoint = context.getEndpoint(ENDPOINT_URI, ApicurioRegistryEndpoint.class);
        endpoint.setRegistryClient(mockClient);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // the default error handler stores the failure on the exchange instead of rethrowing it
                from(ENDPOINT_URI)
                        .process(exchange -> {
                            long globalId = exchange.getIn().getHeader(ApicurioRegistryConstants.HEADER_GLOBAL_ID, Long.class);
                            if (globalId == 1L && attemptsForFirstVersion.incrementAndGet() == 1) {
                                throw new IllegalStateException("simulated failure");
                            }
                        })
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedHeaderValuesReceivedInAnyOrder(ApicurioRegistryConstants.HEADER_GLOBAL_ID, 1L, 2L);
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);

        // version 2 is only delivered after the failed version 1 was retried, in order
        assertThat(globalIds(mock.getReceivedExchanges())).containsExactly(1L, 2L);
        assertThat(attemptsForFirstVersion.get()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void testPooledExchangeReleasedOnFailureAndRetry(boolean fetchContent) throws Exception {
        context.getCamelContextExtension().setExchangeFactory(new PooledExchangeFactory());
        context.getCamelContextExtension().getExchangeFactory().setStatisticsEnabled(true);
        context.getCamelContextExtension().getExchangeFactoryManager().setStatisticsEnabled(true);
        context.start();
        ApicurioRegistryEndpoint endpoint = context.getEndpoint(
                ENDPOINT_URI + "&startScheduler=false&fetchContent=" + fetchContent, ApicurioRegistryEndpoint.class);
        endpoint.setRegistryClient(mockClient);
        pages.add(2, 1);

        IllegalStateException failure = new IllegalStateException("failed first version");
        if (fetchContent) {
            when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                    .versions().byVersionExpression("1").content().get())
                    .thenThrow(failure).thenReturn(new ByteArrayInputStream(new byte[] { 1 }));
            when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                    .versions().byVersionExpression("2").content().get())
                    .thenReturn(new ByteArrayInputStream(new byte[] { 2 }));
        }
        List<Long> delivered = new ArrayList<>();
        List<Exchange> attempted = new ArrayList<>();
        ApicurioRegistryConsumer consumer = (ApicurioRegistryConsumer) endpoint.createConsumer(exchange -> {
            attempted.add(exchange);
            if (!fetchContent && attempted.size() == 1) {
                throw failure;
            }
            delivered.add(exchange.getIn().getHeader(ApicurioRegistryConstants.HEADER_GLOBAL_ID, Long.class));
        });
        consumer.start();
        try {
            var statistics = context.getCamelContextExtension().getExchangeFactoryManager().getStatistics();
            assertThatThrownBy(consumer::poll).isSameAs(failure);
            assertThat(statistics.getReleasedCounter()).isEqualTo(1);
            assertThat(delivered).isEmpty();

            assertThat(consumer.poll()).isEqualTo(2);
            assertThat(delivered).containsExactly(1L, 2L);
            assertThat(statistics.getCreatedCounter()).isEqualTo(1);
            assertThat(statistics.getAcquiredCounter()).isEqualTo(2);
            assertThat(statistics.getReleasedCounter()).isEqualTo(3);
            assertThat(consumer.poll()).isZero();
        } finally {
            consumer.stop();
        }
    }
}
