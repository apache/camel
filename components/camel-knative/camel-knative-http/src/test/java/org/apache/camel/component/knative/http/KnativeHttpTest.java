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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.restassured.RestAssured;
import io.restassured.mapper.ObjectMapperType;
import io.vertx.core.http.HttpServerRequest;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cloudevents.CloudEvent;
import org.apache.camel.component.cloudevents.CloudEvents;
import org.apache.camel.component.knative.KnativeComponent;
import org.apache.camel.component.knative.KnativeEndpoint;
import org.apache.camel.component.knative.spi.Knative;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.restassured.RestAssured.config;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static org.apache.camel.component.knative.KnativeEnvironmentSupport.channel;
import static org.apache.camel.component.knative.KnativeEnvironmentSupport.endpoint;
import static org.apache.camel.component.knative.KnativeEnvironmentSupport.event;
import static org.apache.camel.component.knative.KnativeEnvironmentSupport.sourceChannel;
import static org.apache.camel.component.knative.KnativeEnvironmentSupport.sourceEndpoint;
import static org.apache.camel.component.knative.KnativeEnvironmentSupport.sourceEvent;
import static org.apache.camel.component.knative.http.KnativeHttpTestSupport.configureKnativeComponent;
import static org.apache.camel.component.knative.http.KnativeHttpTestSupport.configurePlatformHttpComponent;
import static org.apache.camel.component.knative.http.KnativeHttpTestSupport.httpAttribute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;

public class KnativeHttpTest {

    private CamelContext context;
    private ProducerTemplate template;
    private int platformHttpPort;
    private String platformHttpHost;

    // **************************
    //
    // Setup
    //
    // **************************

    @BeforeEach
    public void before() {
        this.context = new DefaultCamelContext();
        this.template = this.context.createProducerTemplate();
        this.platformHttpHost = "localhost";
        this.platformHttpPort = AvailablePortFinder.getNextAvailable();

        configurePlatformHttpComponent(context, this.platformHttpPort);

        RestAssured.port = platformHttpPort;
        RestAssured.config = config().encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false));
    }

    @AfterEach
    public void after() {
        ServiceHelper.stopService(template);

        if (this.context != null) {
            this.context.stop();
        }
    }

    // **************************
    //
    // Tests
    //
    // **************************

    void doTestKnativeSource(CloudEvent ce, String basePath, String path) throws Exception {
        KnativeComponent component = configureKnativeComponent(
                context,
                ce,
                sourceEndpoint(
                        "myEndpoint",
                        Map.of(
                                Knative.SERVICE_META_PATH, ObjectHelper.supplyIfEmpty(path, () -> "/"),
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        if (ObjectHelper.isNotEmpty(basePath)) {
            component.getConfiguration().addTransportOptions("basePath", basePath);
        }

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:endpoint/myEndpoint")
                    .to("mock:ce");
        });

        context.start();

        MockEndpoint mock = context.getEndpoint("mock:ce", MockEndpoint.class);
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, ce.version());
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "org.apache.camel.event");
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "/somewhere");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, "text/plain");
        mock.expectedMessagesMatches(e -> e.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_TIME));
        mock.expectedMessagesMatches(e -> e.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_ID));
        mock.expectedBodiesReceived("test");
        mock.expectedMessageCount(1);

        String targetPath = ObjectHelper.supplyIfEmpty(path, () -> "/");
        if (ObjectHelper.isNotEmpty(basePath)) {
            targetPath = basePath + targetPath;
        }

        given()
                .body("test")
                .header(Exchange.CONTENT_TYPE, "text/plain")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version())
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "org.apache.camel.event")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID), "myEventID")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()))
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "/somewhere")
                .when()
                .post(targetPath)
                .then()
                .statusCode(200);

        mock.assertIsSatisfied();
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testKnativeSource(CloudEvent ce) throws Exception {
        doTestKnativeSource(ce, null, null);
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testKnativeSourceWithPath(CloudEvent ce) throws Exception {
        doTestKnativeSource(ce, null, "/a/path");
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testKnativeSourceWithBasePath(CloudEvent ce) throws Exception {
        doTestKnativeSource(ce, "/base", null);
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testKnativeSourceWithBasePathAndPath(CloudEvent ce) throws Exception {
        doTestKnativeSource(ce, "/base", "/a/path");
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testInvokeEndpoint(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                endpoint(
                        Knative.EndpointKind.sink,
                        "myEndpoint",
                        String.format("http://%s:%d/a/path", platformHttpHost, platformHttpPort),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:source")
                    .routeId("my-source")
                    .to("knative:endpoint/myEndpoint");
            b.from("platform-http:/a/path")
                    .convertBodyTo(String.class)
                    .to("mock:ce");
        });

        context.start();

        MockEndpoint mock = context.getEndpoint("mock:ce", MockEndpoint.class);
        mock.expectedHeaderReceived(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version());
        mock.expectedHeaderReceived(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "org.apache.camel.event");
        mock.expectedHeaderReceived(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "my-source");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, "text/plain");
        mock.expectedMessagesMatches(
                e -> e.getMessage().getHeaders().containsKey(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME)));
        mock.expectedMessagesMatches(
                e -> e.getMessage().getHeaders().containsKey(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID)));
        mock.expectedBodiesReceived("test");
        mock.expectedMessageCount(1);

        template.sendBody("direct:source", "test");

        mock.assertIsSatisfied();
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testInvokeEndpointByUrl(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                endpoint(
                        Knative.EndpointKind.sink,
                        "myEndpoint",
                        null,
                        Map.of(
                                Knative.SERVICE_META_URL, String.format("http://localhost:%d/a/path", platformHttpPort),
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:source")
                    .routeId("my-source")
                    .to("knative:endpoint/myEndpoint");
            b.from("platform-http:/a/path")
                    .convertBodyTo(String.class)
                    .to("mock:ce");
        });

        context.start();

        MockEndpoint mock = context.getEndpoint("mock:ce", MockEndpoint.class);
        mock.expectedHeaderReceived(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version());
        mock.expectedHeaderReceived(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "org.apache.camel.event");
        mock.expectedHeaderReceived(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "my-source");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, "text/plain");
        mock.expectedMessagesMatches(
                e -> e.getMessage().getHeaders().containsKey(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME)));
        mock.expectedMessagesMatches(
                e -> e.getMessage().getHeaders().containsKey(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID)));
        mock.expectedBodiesReceived("test");
        mock.expectedMessageCount(1);

        template.sendBody("direct:source", "test");

        mock.assertIsSatisfied();
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testInvokeEndpointByUrlAndPath(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                endpoint(
                        Knative.EndpointKind.sink,
                        "myEndpoint",
                        null,
                        Map.of(
                                Knative.SERVICE_META_PATH, "/with/subpath",
                                Knative.SERVICE_META_URL, String.format("http://localhost:%d/a/path", platformHttpPort),
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:source")
                    .routeId("my-source")
                    .to("knative:endpoint/myEndpoint");
            b.from("platform-http:/a/path/with/subpath")
                    .convertBodyTo(String.class)
                    .to("mock:ce");
        });

        context.start();

        MockEndpoint mock = context.getEndpoint("mock:ce", MockEndpoint.class);
        mock.expectedHeaderReceived(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version());
        mock.expectedHeaderReceived(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "org.apache.camel.event");
        mock.expectedHeaderReceived(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "my-source");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, "text/plain");
        mock.expectedMessagesMatches(
                e -> e.getMessage().getHeaders().containsKey(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME)));
        mock.expectedMessagesMatches(
                e -> e.getMessage().getHeaders().containsKey(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID)));
        mock.expectedBodiesReceived("test");
        mock.expectedMessageCount(1);

        template.sendBody("direct:source", "test");

        mock.assertIsSatisfied();
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testConsumeStructuredContent(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                sourceEndpoint(
                        "myEndpoint",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:endpoint/myEndpoint")
                    .to("mock:ce");
        });

        context.start();

        MockEndpoint mock = context.getEndpoint("mock:ce", MockEndpoint.class);
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, ce.version());
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "org.apache.camel.event");
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_ID, "myEventID");
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "/somewhere");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, "text/plain");
        mock.expectedMessagesMatches(e -> e.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_TIME));
        mock.expectedBodiesReceived("test");
        mock.expectedMessageCount(1);

        if (Objects.equals(CloudEvents.v1_0.version(), ce.version())
                || Objects.equals(CloudEvents.v1_0_1.version(), ce.version())
                || Objects.equals(CloudEvents.v1_0_2.version(), ce.version())) {
            given()
                    .contentType(Knative.MIME_STRUCTURED_CONTENT_MODE)
                    .body(
                            Map.of(
                                    "specversion", ce.version(),
                                    "type", "org.apache.camel.event",
                                    "id", "myEventID",
                                    "time", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()),
                                    "source", "/somewhere",
                                    "datacontenttype", "text/plain",
                                    "data", "test"),
                            ObjectMapperType.JACKSON_2)
                    .when()
                    .post()
                    .then()
                    .statusCode(200);
        } else {
            throw new IllegalArgumentException("Unknown CloudEvent spec: " + ce.version());
        }

        mock.assertIsSatisfied();
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testConsumeContent(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                sourceEndpoint(
                        "myEndpoint",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:endpoint/myEndpoint")
                    .to("mock:ce");
        });

        context.start();

        MockEndpoint mock = context.getEndpoint("mock:ce", MockEndpoint.class);
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, ce.version());
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "org.apache.camel.event");
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_ID, "myEventID");
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "/somewhere");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, "text/plain");
        mock.expectedMessagesMatches(e -> e.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_TIME));
        mock.expectedBodiesReceived("test");
        mock.expectedMessageCount(1);

        given()
                .body("test")
                .header(Exchange.CONTENT_TYPE, "text/plain")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version())
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "org.apache.camel.event")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID), "myEventID")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()))
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "/somewhere")
                .when()
                .post()
                .then()
                .statusCode(200);

        mock.assertIsSatisfied();
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testConsumeContentWithFilter(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                sourceEndpoint(
                        "ep1",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain",
                                Knative.KNATIVE_FILTER_PREFIX + httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "CE1")),
                sourceEndpoint(
                        "ep2",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain",
                                Knative.KNATIVE_FILTER_PREFIX + httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE),
                                "CE2")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:endpoint/ep1")
                    .convertBodyTo(String.class)
                    .to("log:ce1?showAll=true&multiline=true")
                    .to("mock:ce1");
            b.from("knative:endpoint/ep2")
                    .convertBodyTo(String.class)
                    .to("log:ce2?showAll=true&multiline=true")
                    .to("mock:ce2");
        });

        context.start();

        MockEndpoint mock1 = context.getEndpoint("mock:ce1", MockEndpoint.class);
        mock1.expectedMessagesMatches(e -> e.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_TIME));
        mock1.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, ce.version());
        mock1.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "org.apache.camel.event");
        mock1.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_ID, "myEventID1");
        mock1.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "CE1");
        mock1.expectedBodiesReceived("test");
        mock1.expectedMessageCount(1);

        MockEndpoint mock2 = context.getEndpoint("mock:ce2", MockEndpoint.class);
        mock2.expectedMessagesMatches(e -> e.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_TIME));
        mock2.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, ce.version());
        mock2.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "org.apache.camel.event");
        mock2.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_ID, "myEventID2");
        mock2.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "CE2");
        mock2.expectedBodiesReceived("test");
        mock2.expectedMessageCount(1);

        given()
                .body("test")
                .header(Exchange.CONTENT_TYPE, "text/plain")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version())
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "org.apache.camel.event")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID), "myEventID1")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()))
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "CE1")
                .when()
                .post()
                .then()
                .statusCode(200);

        given()
                .body("test")
                .header(Exchange.CONTENT_TYPE, "text/plain")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version())
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "org.apache.camel.event")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID), "myEventID2")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()))
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "CE2")
                .when()
                .post()
                .then()
                .statusCode(200);

        mock1.assertIsSatisfied();
        mock2.assertIsSatisfied();
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testConsumeContentWithRegExFilter(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                sourceEndpoint(
                        "ep1",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain",
                                Knative.KNATIVE_FILTER_PREFIX + httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE),
                                "CE[01234]")),
                sourceEndpoint(
                        "ep2",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain",
                                Knative.KNATIVE_FILTER_PREFIX + httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE),
                                "CE[56789]")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:endpoint/ep1")
                    .convertBodyTo(String.class)
                    .to("log:ce1?showAll=true&multiline=true")
                    .to("mock:ce1");
            b.from("knative:endpoint/ep2")
                    .convertBodyTo(String.class)
                    .to("log:ce2?showAll=true&multiline=true")
                    .to("mock:ce2");
        });

        context.start();

        MockEndpoint mock1 = context.getEndpoint("mock:ce1", MockEndpoint.class);
        mock1.expectedMessagesMatches(e -> e.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_TIME));
        mock1.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, ce.version());
        mock1.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "org.apache.camel.event");
        mock1.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_ID, "myEventID1");
        mock1.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "CE0");
        mock1.expectedBodiesReceived("test");
        mock1.expectedMessageCount(1);

        MockEndpoint mock2 = context.getEndpoint("mock:ce2", MockEndpoint.class);
        mock2.expectedMessagesMatches(e -> e.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_TIME));
        mock2.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, ce.version());
        mock2.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "org.apache.camel.event");
        mock2.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_ID, "myEventID2");
        mock2.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "CE5");
        mock2.expectedBodiesReceived("test");
        mock2.expectedMessageCount(1);

        given()
                .body("test")
                .header(Exchange.CONTENT_TYPE, "text/plain")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version())
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "org.apache.camel.event")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID), "myEventID1")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()))
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "CE0")
                .when()
                .post()
                .then()
                .statusCode(200);

        given()
                .body("test")
                .header(Exchange.CONTENT_TYPE, "text/plain")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version())
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "org.apache.camel.event")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID), "myEventID2")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()))
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "CE5")
                .when()
                .post()
                .then()
                .statusCode(200);

        mock1.assertIsSatisfied();
        mock2.assertIsSatisfied();
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testConsumeEventContent(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                sourceEvent("default"));

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:event/event1")
                    .convertBodyTo(String.class)
                    .to("log:ce1?showAll=true&multiline=true")
                    .to("mock:ce1");
            b.from("knative:event/event2")
                    .convertBodyTo(String.class)
                    .to("log:ce2?showAll=true&multiline=true")
                    .to("mock:ce2");
        });

        context.start();

        MockEndpoint mock1 = context.getEndpoint("mock:ce1", MockEndpoint.class);
        mock1.expectedMessagesMatches(e -> e.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_TIME));
        mock1.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, ce.version());
        mock1.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "event1");
        mock1.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_ID, "myEventID1");
        mock1.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "CE1");
        mock1.expectedBodiesReceived("test");
        mock1.expectedMessageCount(1);

        MockEndpoint mock2 = context.getEndpoint("mock:ce2", MockEndpoint.class);
        mock2.expectedMessagesMatches(e -> e.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_TIME));
        mock2.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, ce.version());
        mock2.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "event2");
        mock2.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_ID, "myEventID2");
        mock2.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "CE2");
        mock2.expectedBodiesReceived("test");
        mock2.expectedMessageCount(1);

        given()
                .body("test")
                .header(Exchange.CONTENT_TYPE, "text/plain")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version())
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "event1")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID), "myEventID1")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()))
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "CE1")
                .when()
                .post()
                .then()
                .statusCode(200);

        given()
                .body("test")
                .header(Exchange.CONTENT_TYPE, "text/plain")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version())
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "event2")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID), "myEventID2")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()))
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "CE2")
                .when()
                .post()
                .then()
                .statusCode(200);

        mock1.assertIsSatisfied();
        mock2.assertIsSatisfied();
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testReply(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                sourceEndpoint(
                        "from",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event.from",
                                Knative.CONTENT_TYPE, "text/plain")),
                endpoint(
                        Knative.EndpointKind.sink,
                        "to",
                        String.format("http://%s:%d", platformHttpHost, platformHttpPort),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event.to",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:endpoint/from")
                    .convertBodyTo(String.class)
                    .setBody()
                    .constant("consumer")
                    .setHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE)
                    .constant("custom");
            b.from("direct:source")
                    .to("knative://endpoint/to")
                    .log("${body}")
                    .to("mock:to");
        });

        MockEndpoint mock = context.getEndpoint("mock:to", MockEndpoint.class);
        mock.expectedBodiesReceived("consumer");
        mock.expectedHeaderReceived(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "org.apache.camel.event.to");
        mock.expectedMessageCount(1);

        context.start();
        template.sendBody("direct:source", "");

        mock.assertIsSatisfied();
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testReplyCloudEventHeaders(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                sourceEndpoint(
                        "from",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")),
                endpoint(
                        Knative.EndpointKind.sink,
                        "to",
                        String.format("http://%s:%d", platformHttpHost, platformHttpPort),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:endpoint/from?replyWithCloudEvent=true")
                    .convertBodyTo(String.class)
                    .setBody()
                    .constant("consumer")
                    .setHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE)
                    .constant("custom");
            b.from("direct:source")
                    .to("knative://endpoint/to")
                    .log("${body}")
                    .to("mock:to");
        });

        MockEndpoint mock = context.getEndpoint("mock:to", MockEndpoint.class);
        mock.expectedBodiesReceived("consumer");
        mock.expectedHeaderReceived(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "custom");
        mock.expectedMessageCount(1);

        context.start();
        template.sendBody("direct:source", "");

        mock.assertIsSatisfied();
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testInvokeServiceWithoutUrl(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                endpoint(
                        Knative.EndpointKind.sink,
                        "test",
                        null,
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:start")
                    .to("knative:endpoint/test")
                    .to("mock:start");
        });

        assertThatExceptionOfType(FailedToStartRouteException.class)
                .isThrownBy(context::start)
                .withCauseExactlyInstanceOf(RuntimeCamelException.class);
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testInvokeNotExistingEndpoint(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                endpoint(
                        Knative.EndpointKind.sink,
                        "test",
                        String.format("http://%s:%d", platformHttpHost, platformHttpPort),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:start")
                    .to("knative:endpoint/test")
                    .to("mock:start");
        });

        context.start();

        Exchange exchange = template.request("direct:start", e -> e.getMessage().setBody(""));
        assertThat(exchange.isFailed()).isTrue();
        assertThat(exchange.getException()).isInstanceOf(CamelException.class);
        assertThat(exchange.getException())
                .hasMessageStartingWith("HTTP operation failed invoking http://localhost:" + platformHttpPort);
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testRemoveConsumer(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                sourceEndpoint(
                        "ep1",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain",
                                Knative.KNATIVE_FILTER_PREFIX + "h", "h1")),
                sourceEndpoint(
                        "ep2",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain",
                                Knative.KNATIVE_FILTER_PREFIX + "h", "h2")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:endpoint/ep1")
                    .routeId("r1")
                    .setBody().simple("${routeId}");
            b.from("knative:endpoint/ep2")
                    .routeId("r2")
                    .setBody().simple("${routeId}");
        });
        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:start")
                    .setHeader("h").body()
                    .toF("http://localhost:%d", platformHttpPort);
        });

        context.start();

        assertThat(template.requestBody("direct:start", "h1", String.class)).isEqualTo("r1");
        assertThat(template.requestBody("direct:start", "h2", String.class)).isEqualTo("r2");

        context.getRouteController().stopRoute("r2");

        assertThat(template.request("direct:start", e -> e.getMessage().setBody("h2"))).satisfies(e -> {
            assertThat(e.isFailed()).isTrue();
            assertThat(e.getException()).isInstanceOf(HttpOperationFailedException.class);
        });
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testAddConsumer(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                sourceEndpoint(
                        "ep1",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain",
                                Knative.KNATIVE_FILTER_PREFIX + "h", "h1")),
                sourceEndpoint(
                        "ep2",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain",
                                Knative.KNATIVE_FILTER_PREFIX + "h", "h2")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:endpoint/ep1")
                    .routeId("r1")
                    .setBody().simple("${routeId}");
        });
        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:start")
                    .setHeader("h").body()
                    .toF("http://localhost:%d", platformHttpPort);
        });

        context.start();

        assertThat(template.requestBody("direct:start", "h1", String.class)).isEqualTo("r1");
        assertThat(template.request("direct:start", e -> e.getMessage().setBody("h2"))).satisfies(e -> {
            assertThat(e.isFailed()).isTrue();
            assertThat(e.getException()).isInstanceOf(HttpOperationFailedException.class);
        });

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:endpoint/ep2")
                    .routeId("r2")
                    .setBody().simple("${routeId}");
        });

        assertThat(template.requestBody("direct:start", "h1", String.class)).isEqualTo("r1");
        assertThat(template.requestBody("direct:start", "h2", String.class)).isEqualTo("r2");
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testInvokeEndpointWithError(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                endpoint(
                        Knative.EndpointKind.sink,
                        "ep",
                        String.format("http://%s:%d", platformHttpHost, platformHttpPort),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:start")
                    .to("knative:endpoint/ep")
                    .to("mock:start");
            b.fromF("platform-http:/")
                    .routeId("endpoint")
                    .process(e -> {
                        throw new RuntimeException("endpoint error");
                    });
        });

        context.start();

        Exchange exchange = template.request("direct:start", e -> e.getMessage().setBody(""));
        assertThat(exchange.isFailed()).isTrue();
        assertThat(exchange.getException()).isInstanceOf(CamelException.class);
        assertThat(exchange.getException()).hasMessageStartingWith("HTTP operation failed invoking");
        assertThat(exchange.getException()).hasMessageContaining("with statusCode: 500, statusMessage: Internal Server Error");
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testEvents(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                event(
                        Knative.EndpointKind.sink,
                        "default",
                        String.format("http://%s:%d", platformHttpHost, platformHttpPort),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")),
                sourceEvent(
                        "default",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:source")
                    .to("knative:event/myEvent");
            b.from("knative:event/myEvent")
                    .to("mock:ce");
        });

        context.start();

        MockEndpoint mock = context.getEndpoint("mock:ce", MockEndpoint.class);
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, ce.version());
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "myEvent");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, "text/plain");
        mock.expectedMessagesMatches(e -> e.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_TIME));
        mock.expectedMessagesMatches(e -> e.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_ID));
        mock.expectedBodiesReceived("test");
        mock.expectedMessageCount(1);

        template.sendBody("direct:source", "test");

        mock.assertIsSatisfied();
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testEventsNoName(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                event(
                        Knative.EndpointKind.sink,
                        "default",
                        String.format("http://%s:%d", platformHttpHost, platformHttpPort),
                        Map.of(
                                Knative.CONTENT_TYPE, "text/plain")),
                sourceEvent(
                        "default",
                        Map.of(
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:source")
                    .to("knative:event");
            b.from("knative:event")
                    .to("mock:ce");
        });

        context.start();

        MockEndpoint mock = context.getEndpoint("mock:ce", MockEndpoint.class);
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, ce.version());
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "org.apache.camel.event");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, "text/plain");
        mock.expectedMessagesMatches(e -> e.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_TIME));
        mock.expectedMessagesMatches(e -> e.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_ID));
        mock.expectedBodiesReceived("test");
        mock.expectedMessageCount(1);

        template.sendBody("direct:source", "test");

        mock.assertIsSatisfied();
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testEventsWithResourceRef(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                event(
                        Knative.EndpointKind.sink,
                        "default",
                        String.format("http://%s:%d", platformHttpHost, platformHttpPort),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain",
                                Knative.KNATIVE_OBJECT_KIND, "MyObject",
                                Knative.KNATIVE_OBJECT_API_VERSION, "v1",
                                Knative.KNATIVE_OBJECT_NAME, "myName1")),
                sourceEvent(
                        "default",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain",
                                Knative.KNATIVE_OBJECT_KIND, "MyOtherObject",
                                Knative.KNATIVE_OBJECT_API_VERSION, "v2",
                                Knative.KNATIVE_OBJECT_NAME, "myName2")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:source")
                    .to("knative:event/myEvent?kind=MyObject&apiVersion=v1&name=myName1");
            b.from("knative:event/myEvent?kind=MyOtherObject&apiVersion=v2&name=myName2")
                    .to("mock:ce");
        });

        context.start();

        assertThat(context.getEndpoint("knative:event/myEvent?kind=MyObject&apiVersion=v1&name=myName1", KnativeEndpoint.class))
                .satisfies(e -> {
                    assertThat(e.getType()).isEqualTo(Knative.Type.event);
                    assertThat(e.getTypeId()).isEqualTo("myEvent");
                    assertThat(e.getConfiguration().getTypeId()).isEqualTo("myEvent");
                    assertThat(e.getConfiguration().getName()).isEqualTo("myName1");
                });
        assertThat(context.getEndpoint("knative:event/myEvent?kind=MyOtherObject&apiVersion=v2&name=myName2",
                KnativeEndpoint.class)).satisfies(e -> {
                    assertThat(e.getType()).isEqualTo(Knative.Type.event);
                    assertThat(e.getTypeId()).isEqualTo("myEvent");
                    assertThat(e.getConfiguration().getTypeId()).isEqualTo("myEvent");
                    assertThat(e.getConfiguration().getName()).isEqualTo("myName2");
                });

        MockEndpoint mock = context.getEndpoint("mock:ce", MockEndpoint.class);
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, ce.version());
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "myEvent");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, "text/plain");
        mock.expectedMessagesMatches(e -> e.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_TIME));
        mock.expectedMessagesMatches(e -> e.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_ID));
        mock.expectedBodiesReceived("test");
        mock.expectedMessageCount(1);

        template.sendBody("direct:source", "test");

        mock.assertIsSatisfied();
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testConsumeContentWithResourceRef(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                sourceEndpoint(
                        "myEndpoint",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain",
                                Knative.KNATIVE_OBJECT_KIND, "MyObject",
                                Knative.KNATIVE_OBJECT_API_VERSION, "v1",
                                Knative.KNATIVE_OBJECT_NAME, "myName1")),
                sourceEndpoint(
                        "myEndpoint",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain",
                                Knative.KNATIVE_OBJECT_KIND, "MyObject",
                                Knative.KNATIVE_OBJECT_API_VERSION, "v2",
                                Knative.KNATIVE_OBJECT_NAME, "myName2")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:endpoint/myEndpoint?kind=MyObject&apiVersion=v2&name=myName2")
                    .to("mock:ce");
        });

        context.start();

        assertThat(context.getEndpoint("knative:endpoint/myEndpoint?kind=MyObject&apiVersion=v2&name=myName2",
                KnativeEndpoint.class)).satisfies(e -> {
                    assertThat(e.getType()).isEqualTo(Knative.Type.endpoint);
                    assertThat(e.getTypeId()).isEqualTo("myEndpoint");
                    assertThat(e.getConfiguration().getTypeId()).isEqualTo("myEndpoint");
                    assertThat(e.getConfiguration().getName()).isEqualTo("myName2");
                });

        MockEndpoint mock = context.getEndpoint("mock:ce", MockEndpoint.class);
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, ce.version());
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "org.apache.camel.event");
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_ID, "myEventID");
        mock.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "/somewhere");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, "text/plain");
        mock.expectedMessagesMatches(e -> e.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_TIME));
        mock.expectedBodiesReceived("test");
        mock.expectedMessageCount(1);

        given()
                .body("test")
                .header(Exchange.CONTENT_TYPE, "text/plain")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version())
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "org.apache.camel.event")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID), "myEventID")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()))
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "/somewhere")
                .when()
                .post()
                .then()
                .statusCode(200);

        mock.assertIsSatisfied();
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testWrongMethod(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                sourceEndpoint(
                        "myEndpoint",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:endpoint/myEndpoint")
                    .to("mock:ce");
        });

        context.start();

        given()
                .body("test")
                .header(Exchange.CONTENT_TYPE, "text/plain")
                .when()
                .get()
                .then()
                .statusCode(405);
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testNoBody(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                endpoint(
                        Knative.EndpointKind.sink,
                        "myEndpoint",
                        String.format("http://%s:%d", platformHttpHost, platformHttpPort),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:start")
                    .to("knative:endpoint/myEndpoint");
        });

        context.start();

        Exchange exchange = template.request("direct:start", e -> e.getMessage().setBody(null));
        assertThat(exchange.isFailed()).isTrue();
        assertThat(exchange.getException()).isInstanceOf(IllegalArgumentException.class);
        assertThat(exchange.getException()).hasMessage("body must not be null");
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testNoContent(CloudEvent ce) throws Exception {
        final KnativeHttpServer server = new KnativeHttpServer(context, event -> {
            event.response().setStatusCode(204);
            event.response().end("");
        });

        configureKnativeComponent(
                context,
                ce,
                channel(
                        Knative.EndpointKind.source,
                        "messages",
                        null,
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")),
                channel(
                        Knative.EndpointKind.sink,
                        "messages",
                        String.format("http://%s:%d", platformHttpHost, platformHttpPort),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")),
                channel(
                        Knative.EndpointKind.sink,
                        "words",
                        String.format("http://%s:%d", server.getHost(), server.getPort()),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        try {
            server.start();

            RouteBuilder.addRoutes(context, b -> {
                b.from("knative:channel/messages")
                        .transform().simple("transformed ${body}")
                        .log("${body}")
                        .to("knative:channel/words");
            });

            context.start();

            Exchange exchange = template.request("knative:channel/messages", e -> e.getMessage().setBody("message"));
            assertThat(exchange.getMessage().getHeaders()).containsEntry(Exchange.HTTP_RESPONSE_CODE, 204);
            assertThat(exchange.getMessage().getBody()).isNull();
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testNoReply(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                sourceChannel(
                        "channel",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:channel/channel?reply=false")
                    .setBody().constant(Map.of());
        });

        context.start();

        given()
                .body("test")
                .header(Exchange.CONTENT_TYPE, "text/plain")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version())
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "org.apache.camel.event")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID), "myEventID")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()))
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "/somewhere")
                .when()
                .post()
                .then()
                .statusCode(204)
                .body(is(emptyOrNullString()));
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testNoReplyMeta(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                sourceChannel(
                        "channel",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain",
                                Knative.KNATIVE_REPLY, "false")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:channel/channel")
                    .setBody().constant(Map.of());
        });

        context.start();

        given()
                .body("test")
                .header(Exchange.CONTENT_TYPE, "text/plain")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version())
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "org.apache.camel.event")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID), "myEventID")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()))
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "/somewhere")
                .when()
                .post()
                .then()
                .statusCode(204)
                .body(is(emptyOrNullString()));
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testNoReplyMetaOverride(CloudEvent ce) throws Exception {
        configureKnativeComponent(
                context,
                ce,
                sourceChannel(
                        "channel",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain",
                                Knative.KNATIVE_REPLY, "true")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:channel/channel?reply=false")
                    .setBody().constant(Map.of());
        });

        context.start();

        given()
                .body("test")
                .header(Exchange.CONTENT_TYPE, "text/plain")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version())
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "org.apache.camel.event")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID), "myEventID")
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()))
                .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "/somewhere")
                .when()
                .post()
                .then()
                .statusCode(204)
                .body(is(emptyOrNullString()));
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testHeaders(CloudEvent ce) throws Exception {
        final int port = AvailablePortFinder.getNextAvailable();
        final KnativeHttpServer server = new KnativeHttpServer(context);

        configureKnativeComponent(
                context,
                ce,
                endpoint(
                        Knative.EndpointKind.sink,
                        "ep",
                        String.format("http://%s:%d", server.getHost(), server.getPort()),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:start")
                    .setHeader("CamelDummyHeader").constant("test")
                    .to("knative:endpoint/ep")
                    .to("direct:mock");
            b.from("direct:mock")
                    .to("mock:ep");
        });

        context.start();

        try {
            MockEndpoint mock = context.getEndpoint("mock:ep", MockEndpoint.class);
            mock.expectedHeaderReceived("CamelDummyHeader", "test");
            mock.expectedMessageCount(1);

            server.start();

            template.sendBody("direct:start", "");

            mock.assertIsSatisfied();

            HttpServerRequest request = server.poll(30, TimeUnit.SECONDS);
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION))).isEqualTo(ce.version());
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE)))
                    .isEqualTo("org.apache.camel.event");
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID))).isNotNull();
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE))).isNotNull();
            assertThat(request.getHeader(Exchange.CONTENT_TYPE)).isEqualTo("text/plain");
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testHeadersInReply(CloudEvent ce) throws Exception {
        final KnativeHttpServer server = new KnativeHttpServer(context);

        configureKnativeComponent(
                context,
                ce,
                endpoint(
                        Knative.EndpointKind.sink,
                        "ep",
                        String.format("http://%s:%d", server.getHost(), server.getPort()),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:start")
                    .setHeader("CamelDummyHeader").constant("test")
                    .to("knative:endpoint/ep");
        });

        context.start();

        try {
            MockEndpoint mock = context.getEndpoint("mock:ep", MockEndpoint.class);
            mock.expectedHeaderReceived("CamelDummyHeader", "test");
            mock.expectedMessageCount(1);

            server.start();

            Exchange exchange = template.request("direct:start", e -> e.getMessage().setBody("test"));
            assertThat(exchange.getMessage().getHeaders()).containsEntry("CamelDummyHeader", "test");
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testHeadersOverrideFromEnv(CloudEvent ce) throws Exception {
        final KnativeHttpServer server = new KnativeHttpServer(context);
        final String typeHeaderKey = httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE);
        final String typeHeaderVal = UUID.randomUUID().toString();
        final String sourceHeaderKey = httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE);
        final String sourceHeaderVal = UUID.randomUUID().toString();

        configureKnativeComponent(
                context,
                ce,
                endpoint(
                        Knative.EndpointKind.sink,
                        "ep",
                        String.format("http://%s:%d", server.getHost(), server.getPort()),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain",
                                Knative.KNATIVE_CE_OVERRIDE_PREFIX + typeHeaderKey, typeHeaderVal,
                                Knative.KNATIVE_CE_OVERRIDE_PREFIX + sourceHeaderKey, sourceHeaderVal)));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:start")
                    .to("knative:endpoint/ep");
        });

        context.start();
        try {
            server.start();
            template.sendBody("direct:start", "");

            HttpServerRequest request = server.poll(30, TimeUnit.SECONDS);
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION))).isEqualTo(ce.version());
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE))).isEqualTo(typeHeaderVal);
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID))).isNotNull();
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE))).isEqualTo(sourceHeaderVal);
            assertThat(request.getHeader(Exchange.CONTENT_TYPE)).isEqualTo("text/plain");
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testHeadersOverrideFromURI(CloudEvent ce) throws Exception {
        final KnativeHttpServer server = new KnativeHttpServer(context);
        final String typeHeaderKey = httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE);
        final String typeHeaderVal = UUID.randomUUID().toString();
        final String sourceHeaderKey = httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE);
        final String sourceHeaderVal = UUID.randomUUID().toString();

        configureKnativeComponent(
                context,
                ce,
                endpoint(
                        Knative.EndpointKind.sink,
                        "ep",
                        String.format("http://%s:%d", server.getHost(), server.getPort()),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:start")
                    .toF("knative:endpoint/ep?%s=%s&%s=%s",
                            Knative.KNATIVE_CE_OVERRIDE_PREFIX + typeHeaderKey, typeHeaderVal,
                            Knative.KNATIVE_CE_OVERRIDE_PREFIX + sourceHeaderKey, sourceHeaderVal);
        });

        context.start();
        try {
            server.start();
            template.sendBody("direct:start", "");

            HttpServerRequest request = server.poll(30, TimeUnit.SECONDS);
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION))).isEqualTo(ce.version());
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE))).isEqualTo(typeHeaderVal);
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID))).isNotNull();
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE))).isEqualTo(sourceHeaderVal);
            assertThat(request.getHeader(Exchange.CONTENT_TYPE)).isEqualTo("text/plain");
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testHeadersOverrideFromConf(CloudEvent ce) throws Exception {
        final KnativeHttpServer server = new KnativeHttpServer(context);
        final String typeHeaderKey = httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE);
        final String typeHeaderVal = UUID.randomUUID().toString();
        final String sourceHeaderKey = httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE);
        final String sourceHeaderVal = UUID.randomUUID().toString();

        KnativeComponent component = configureKnativeComponent(
                context,
                ce,
                endpoint(
                        Knative.EndpointKind.sink,
                        "ep",
                        String.format("http://%s:%d", server.getHost(), server.getPort()),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        component.getConfiguration().setCeOverride(Map.of(
                Knative.KNATIVE_CE_OVERRIDE_PREFIX + typeHeaderKey, typeHeaderVal,
                Knative.KNATIVE_CE_OVERRIDE_PREFIX + sourceHeaderKey, sourceHeaderVal));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:start")
                    .to("knative:endpoint/ep");
        });

        context.start();
        try {
            server.start();
            template.sendBody("direct:start", "");

            HttpServerRequest request = server.poll(30, TimeUnit.SECONDS);
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION))).isEqualTo(ce.version());
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE))).isEqualTo(typeHeaderVal);
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID))).isNotNull();
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE))).isEqualTo(sourceHeaderVal);
            assertThat(request.getHeader(Exchange.CONTENT_TYPE)).isEqualTo("text/plain");
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testHeadersOverrideFromRouteWithCamelHeader(CloudEvent ce) throws Exception {
        final KnativeHttpServer server = new KnativeHttpServer(context);

        configureKnativeComponent(
                context,
                ce,
                endpoint(
                        Knative.EndpointKind.sink,
                        "ep",
                        String.format("http://%s:%d", server.getHost(), server.getPort()),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:start")
                    .routeId("my-source")
                    .setHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE).constant("myType")
                    .to("knative:endpoint/ep");
        });

        context.start();
        try {
            server.start();
            template.sendBody("direct:start", "");

            HttpServerRequest request = server.poll(30, TimeUnit.SECONDS);
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION))).isEqualTo(ce.version());
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE))).isEqualTo("myType");
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID))).isNotNull();
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE))).isEqualTo("my-source");
            assertThat(request.getHeader(Exchange.CONTENT_TYPE)).isEqualTo("text/plain");
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testHeadersOverrideFromRouteWithCEHeader(CloudEvent ce) throws Exception {
        final KnativeHttpServer server = new KnativeHttpServer(context);

        configureKnativeComponent(
                context,
                ce,
                endpoint(
                        Knative.EndpointKind.sink,
                        "ep",
                        String.format("http://%s:%d", server.getHost(), server.getPort()),
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:start")
                    .routeId("my-source-x")
                    .setHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE)).constant("fromCEHeader")
                    .setHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE).constant("fromCamelHeader")
                    .to("knative:endpoint/ep");
        });

        context.start();
        try {
            server.start();
            template.sendBody("direct:start", "");

            HttpServerRequest request = server.poll(30, TimeUnit.SECONDS);
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION))).isEqualTo(ce.version());
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE))).isEqualTo("fromCEHeader");
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID))).isNotNull();
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE))).isEqualTo("my-source-x");
            assertThat(request.getHeader(Exchange.CONTENT_TYPE)).isEqualTo("text/plain");
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testEventBridge(CloudEvent ce) throws Exception {
        final KnativeHttpServer server = new KnativeHttpServer(context);

        configureKnativeComponent(
                context,
                ce,
                event(
                        Knative.EndpointKind.sink,
                        "event.sink",
                        String.format("http://%s:%d", server.getHost(), server.getPort()),
                        Map.of(
                                Knative.CONTENT_TYPE, "text/plain")),
                sourceEvent(
                        "event.source",
                        Map.of(
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:event/event.source")
                    .to("knative:event/event.sink");
        });

        context.start();

        try {
            server.start();

            given()
                    .body("test")
                    .header(Exchange.CONTENT_TYPE, "text/plain")
                    .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version())
                    .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "event.source")
                    .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID), "myEventID")
                    .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME),
                            DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()))
                    .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "/somewhere")
                    .when()
                    .post()
                    .then()
                    .statusCode(204);

            HttpServerRequest request = server.poll(30, TimeUnit.SECONDS);
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION))).isEqualTo(ce.version());
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE))).isEqualTo("event.sink");
            assertThat(request.getHeader(Exchange.CONTENT_TYPE)).isEqualTo("text/plain");
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testDynamicEventBridge(CloudEvent ce) throws Exception {
        final KnativeHttpServer server = new KnativeHttpServer(context);

        configureKnativeComponent(
                context,
                ce,
                event(
                        Knative.EndpointKind.sink,
                        "default",
                        String.format("http://%s:%d", server.getHost(), server.getPort()),
                        Map.of(
                                Knative.CONTENT_TYPE, "text/plain")),
                sourceEvent(
                        "event.source",
                        Map.of(
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("knative:event/event.source")
                    .setHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE).constant("event.sink")
                    .to("knative:event");
        });

        context.start();

        try {
            server.start();

            given()
                    .body("test")
                    .header(Exchange.CONTENT_TYPE, "text/plain")
                    .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version())
                    .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "event.source")
                    .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID), "myEventID")
                    .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME),
                            DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()))
                    .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "/somewhere")
                    .when()
                    .post()
                    .then()
                    .statusCode(204);

            HttpServerRequest request = server.poll(30, TimeUnit.SECONDS);
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION))).isEqualTo(ce.version());
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE))).isEqualTo("event.sink");
            assertThat(request.getHeader(Exchange.CONTENT_TYPE)).isEqualTo("text/plain");
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testEventDefaultType(CloudEvent ce) throws Exception {
        final KnativeHttpServer server = new KnativeHttpServer(context);

        configureKnativeComponent(
                context,
                ce,
                event(
                        Knative.EndpointKind.sink,
                        "default",
                        String.format("http://%s:%d", server.getHost(), server.getPort()),
                        Map.of(
                                Knative.CONTENT_TYPE, "text/plain")));

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:start")
                    .to("knative:event");
        });

        context.start();
        try {
            server.start();
            template.sendBody("direct:start", "");

            HttpServerRequest request = server.poll(30, TimeUnit.SECONDS);
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION))).isEqualTo(ce.version());
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE)))
                    .isEqualTo("org.apache.camel.event");
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID))).isNotNull();
            assertThat(request.getHeader(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE))).isNotNull();
            assertThat(request.getHeader(Exchange.CONTENT_TYPE)).isEqualTo("text/plain");
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(CloudEvents.class)
    void testSlowConsumer(CloudEvent ce) throws Exception {
        final KnativeHttpServer server = new KnativeHttpServer(context, event -> {
            event.vertx().executeBlocking(() -> {
                try {
                    Thread.sleep(5000);
                    return null;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }, false)
                    .onComplete(result -> {
                        event.response().setStatusCode(200);
                        event.response().end("");
                    });
        });

        configureKnativeComponent(
                context,
                ce,
                sourceEndpoint(
                        "start",
                        Map.of(
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                                Knative.CONTENT_TYPE, "text/plain")));

        try {
            server.start();

            RouteBuilder.addRoutes(context, b -> {
                b.from("knative:endpoint/start")
                        .removeHeaders("Camel*")
                        .toF("http://%s:%d", server.getHost(), server.getPort());
            });

            context.start();

            given()
                    .body("test")
                    .header(Exchange.CONTENT_TYPE, "text/plain")
                    .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), ce.version())
                    .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "org.apache.camel.event")
                    .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_ID), "myEventID")
                    .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_TIME),
                            DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()))
                    .header(httpAttribute(ce, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "/somewhere")
                    .when()
                    .post()
                    .then()
                    .statusCode(200);
        } finally {
            server.stop();
        }
    }
}
