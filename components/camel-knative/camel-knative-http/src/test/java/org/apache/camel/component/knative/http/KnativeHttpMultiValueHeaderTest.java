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
package org.apache.camel.component.knative.http;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.http.HttpServerRequest;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.cloudevents.CloudEvents;
import org.apache.camel.component.knative.spi.Knative;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.restassured.RestAssured.given;
import static org.apache.camel.component.knative.KnativeEnvironmentSupport.endpoint;
import static org.apache.camel.component.knative.KnativeEnvironmentSupport.sourceEndpoint;
import static org.apache.camel.component.knative.http.KnativeHttpTestSupport.configureKnativeComponent;
import static org.apache.camel.component.knative.http.KnativeHttpTestSupport.configurePlatformHttpComponent;
import static org.apache.camel.component.knative.http.KnativeHttpTestSupport.httpAttribute;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A header with several values (a collection or an array, as the consumer maps a request header that occurs more than
 * once) must be sent as one HTTP header per value, in the request of the producer and in the reply of the consumer.
 */
class KnativeHttpMultiValueHeaderTest {

    private static final CloudEvent CE = CloudEvents.v1_0;

    @RegisterExtension
    AvailablePortFinder.Port platformHttpPort = AvailablePortFinder.find();

    private CamelContext context;
    private ProducerTemplate template;
    private KnativeHttpServer server;

    @BeforeEach
    void before() {
        context = new DefaultCamelContext();
        template = context.createProducerTemplate();
        configurePlatformHttpComponent(context, platformHttpPort.getPort());
        RestAssured.port = platformHttpPort.getPort();
    }

    @AfterEach
    void after() {
        ServiceHelper.stopService(template);
        // the server stops on a thread of the context, so it must be stopped first
        if (server != null) {
            server.stop();
        }
        context.stop();
    }

    @Test
    void producerSendsEachValueOfAListHeader() throws Exception {
        HttpServerRequest request = sendWithHeader(List.of("a", "b"));

        assertThat(request.headers().getAll("X-Test")).containsExactly("a", "b");
    }

    @Test
    void producerSendsEachValueOfAnArrayHeader() throws Exception {
        HttpServerRequest request = sendWithHeader(new String[] { "a", "b" });

        assertThat(request.headers().getAll("X-Test")).containsExactly("a", "b");
    }

    @Test
    void producerSendsAStringHeaderWithACommaAsOneValue() throws Exception {
        HttpServerRequest request = sendWithHeader("a, b");

        assertThat(request.headers().getAll("X-Test")).containsExactly("a, b");
    }

    @Test
    void producerSkipsTheMissingValuesOfAListHeader() throws Exception {
        HttpServerRequest request = sendWithHeader(Arrays.asList("a", null, "b"));

        assertThat(request.headers().getAll("X-Test")).containsExactly("a", "b");
    }

    @Test
    void producerDoesNotSendAHeaderForAnEmptyList() throws Exception {
        // a collection without values has no header to send (it was sent as the value "[]")
        HttpServerRequest request = sendWithHeader(List.of());

        assertThat(request.headers().contains("X-Test")).isFalse();
    }

    @Test
    void producerDoesNotSendAHeaderForAnEmptyArray() throws Exception {
        HttpServerRequest request = sendWithHeader(new Object[0]);

        assertThat(request.headers().contains("X-Test")).isFalse();
    }

    @Test
    void producerDoesNotSendAHeaderForAListWithOnlyNullValues() throws Exception {
        HttpServerRequest request = sendWithHeader(Arrays.asList(null, null));

        assertThat(request.headers().contains("X-Test")).isFalse();
    }

    @Test
    void producerSendsAByteArrayHeaderAsOneValue() throws Exception {
        // a byte[] (such as a Kafka record header) is one value and not one header per byte
        HttpServerRequest request = sendWithHeader("abc".getBytes(StandardCharsets.UTF_8));

        assertThat(request.headers().getAll("X-Test")).containsExactly("abc");
    }

    @Test
    void consumerRepliesWithEachValueOfAListHeader() throws Exception {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("X-Test", List.of("a", "b"));
        headers.put("X-Single", "c");
        Response response = replyWithHeaders(headers);

        assertThat(response.headers().getValues("X-Test")).containsExactly("a", "b");
        assertThat(response.headers().getValues("X-Single")).containsExactly("c");
    }

    @Test
    void consumerRepliesWithoutAHeaderForAnEmptyList() throws Exception {
        Response response = replyWithHeaders(Map.of("X-Test", List.of()));

        assertThat(response.headers().hasHeaderWithName("X-Test")).isFalse();
    }

    @Test
    void consumerRepliesWithAByteArrayHeaderAsOneValue() throws Exception {
        Response response = replyWithHeaders(Map.of("X-Test", "abc".getBytes(StandardCharsets.UTF_8)));

        assertThat(response.headers().getValues("X-Test")).containsExactly("abc");
    }

    /**
     * Sends a request to a Knative consumer route that sets the given headers and returns its reply.
     */
    private Response replyWithHeaders(Map<String, Object> headers) throws Exception {
        configureKnativeComponent(context, CE,
                sourceEndpoint("from", Map.of(
                        Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                        Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> b.from("knative:endpoint/from")
                .process(e -> headers.forEach(e.getMessage()::setHeader))
                .setBody().constant("reply"));
        context.start();

        Response response = given()
                .body("test")
                .header(Exchange.CONTENT_TYPE, "text/plain")
                .header(httpAttribute(CE, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), CE.version())
                .header(httpAttribute(CE, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "org.apache.camel.event")
                .header(httpAttribute(CE, CloudEvent.CAMEL_CLOUD_EVENT_ID), "myEventID")
                .header(httpAttribute(CE, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "/somewhere")
                .when()
                .post();

        assertThat(response.statusCode()).isEqualTo(200);
        return response;
    }

    /**
     * Sends a message with the header X-Test to a Knative sink and returns the HTTP request the sink received.
     */
    private HttpServerRequest sendWithHeader(Object value) throws Exception {
        server = new KnativeHttpServer(context);
        configureKnativeComponent(context, CE,
                endpoint(Knative.EndpointKind.sink, "ep",
                        String.format("http://%s:%d", server.getHost(), server.getPort()),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> b.from("direct:start").to("knative:endpoint/ep"));
        context.start();
        server.start();

        Exchange exchange = template.request("direct:start", e -> {
            e.getMessage().setBody("test");
            e.getMessage().setHeader("X-Test", value);
        });
        assertThat(exchange.getException()).isNull();

        HttpServerRequest request = server.poll(30, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        return request;
    }
}
