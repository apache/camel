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
package org.apache.camel.component.kubernetes.consumer.integration;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.MicroTimeBuilder;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.api.model.events.v1.EventBuilder;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "kubernetes.test.auth", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host.k8s", matches = "true", disabledReason = "Requires kubernetes"),
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KubernetesEventsConsumerIT extends KubernetesTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    private void setupFullEventWithHeaders(Exchange exchange) {
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_NAME, "test");
        Map<String, String> labels = new HashMap<>();
        labels.put("this", "rocks");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENTS_LABELS, labels);
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_TIME, "2022-10-10T17:30:47.986439Z");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION_PRODUCER, "Some Action");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_TYPE, "Warning");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_REASON, "Some Reason");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_NOTE, "Some Note");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_REGARDING,
                new ObjectReferenceBuilder().withName("Some Regarding").build());
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_RELATED,
                new ObjectReferenceBuilder().withName("Some Related").build());
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_REPORTING_CONTROLLER, "Some-Reporting-Controller");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_REPORTING_INSTANCE, "Some-Reporting-Instance");

    }

    @Test
    @Order(1)
    void createFullEventWithHeaders() throws Exception {
        mockResultEndpoint.expectedMessageCount(1);
        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_EVENT_ACTION, "ADDED");
        Exchange ex = template.request("direct:createEvent", this::setupFullEventWithHeaders);

        assertNotNull(ex);
        assertNotNull(ex.getMessage());
        assertNotNull(ex.getMessage().getBody());

        Event evt = ex.getMessage().getBody(Event.class);
        assertEquals("test", evt.getMetadata().getName());
        assertEquals(Map.of("this", "rocks"), evt.getMetadata().getLabels());
        assertEquals("default", evt.getMetadata().getNamespace());
        assertEquals(new MicroTimeBuilder().withTime("2022-10-10T17:30:47.986439Z").build(), evt.getEventTime());
        assertEquals("Some Action", evt.getAction());
        assertEquals("Warning", evt.getType());
        assertEquals("Some Reason", evt.getReason());
        assertEquals("Some Note", evt.getNote());
        assertEquals("Some Regarding", evt.getRegarding().getName());
        assertEquals("Some Related", evt.getRelated().getName());
        assertEquals("Some-Reporting-Controller", evt.getReportingController());
        assertEquals("Some-Reporting-Instance", evt.getReportingInstance());

        mockResultEndpoint.assertIsSatisfied();
    }

    private void setupMinimalEventWithHeaders(Exchange exchange) {
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_NAME, "test1");
        Map<String, String> labels = new HashMap<>();
        labels.put("this", "rocks");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENTS_LABELS, labels);
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION_PRODUCER, "Some Action");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_TYPE, "Normal");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_REASON, "Some Reason");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_REPORTING_CONTROLLER, "Some-Reporting-Controller");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_REPORTING_INSTANCE, "Some-Reporting-Instance");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_TIME, "2022-10-10T17:30:47.986439Z");
    }

    @Test
    @Order(2)
    void createMinimalEventWithHeaders() throws Exception {
        mockResultEndpoint.expectedMessageCount(1);
        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_EVENT_ACTION, "ADDED");
        Exchange ex = template.request("direct:createEvent", this::setupMinimalEventWithHeaders);

        assertNotNull(ex);
        assertNotNull(ex.getMessage());
        assertNotNull(ex.getMessage().getBody());

        Event evt = ex.getMessage().getBody(Event.class);
        assertEquals("test1", evt.getMetadata().getName());
        assertEquals(Map.of("this", "rocks"), evt.getMetadata().getLabels());
        assertEquals("default", evt.getMetadata().getNamespace());
        assertNotNull(evt.getEventTime());
        assertEquals("Some Action", evt.getAction());
        assertEquals("Normal", evt.getType());
        assertEquals("Some Reason", evt.getReason());
        assertEquals("Some-Reporting-Controller", evt.getReportingController());
        assertEquals("Some-Reporting-Instance", evt.getReportingInstance());
        assertEquals(new MicroTimeBuilder().withTime("2022-10-10T17:30:47.986439Z").build(), evt.getEventTime());

        mockResultEndpoint.assertIsSatisfied();
    }

    private void setupMinimalEventWithBuilder(Exchange exchange) {
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_NAME, "test2");
        Map<String, String> labels = new HashMap<>();
        labels.put("this", "rocks");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENTS_LABELS, labels);
        exchange.getIn().setBody(
                new EventBuilder()
                        .withAction("Some Action")
                        .withType("Normal")
                        .withReason("Some Reason")
                        .withReportingController("Some-Reporting-Controller")
                        .withReportingInstance("Some-Reporting-Instance")
                        .withEventTime(new MicroTimeBuilder().withTime("2022-10-10T19:33:47.986439Z").build()));
    }

    @Test
    @Order(3)
    void createMinimalEventWithBuilder() throws Exception {
        mockResultEndpoint.expectedMessageCount(1);
        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_EVENT_ACTION, "ADDED");
        Exchange ex = template.request("direct:createEvent", this::setupMinimalEventWithBuilder);

        assertNotNull(ex);
        assertNotNull(ex.getMessage());
        assertNotNull(ex.getMessage().getBody());

        Event evt = ex.getMessage().getBody(Event.class);
        assertEquals("test2", evt.getMetadata().getName());
        assertEquals(Map.of("this", "rocks"), evt.getMetadata().getLabels());
        assertEquals("default", evt.getMetadata().getNamespace());
        assertNotNull(evt.getEventTime());
        assertEquals("Some Action", evt.getAction());
        assertEquals("Normal", evt.getType());
        assertEquals("Some Reason", evt.getReason());
        assertEquals("Some-Reporting-Controller", evt.getReportingController());
        assertEquals("Some-Reporting-Instance", evt.getReportingInstance());

        mockResultEndpoint.assertIsSatisfied();
    }

    @ParameterizedTest
    @ValueSource(strings = { "test", "test1", "test2" })
    @Order(4)
    void deleteEvent(String eventName) throws Exception {
        mockResultEndpoint.expectedMessageCount(1);
        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_EVENT_ACTION, "DELETED");
        Exchange ex = template.request("direct:deleteEvent", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_NAME, eventName);
        });

        boolean eventDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(eventDeleted);

        mockResultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createEvent").toF("kubernetes-events://%s?oauthToken=%s&operation=createEvent", host, authToken);
                from("direct:deleteEvent").toF("kubernetes-events://%s?oauthToken=%s&operation=deleteEvent", host, authToken);
                fromF("kubernetes-events://%s?oauthToken=%s&namespace=default&labelKey=this&labelValue=rocks", host, authToken)
                        .process(new KubernetesProcessor())
                        .to(mockResultEndpoint);
            }
        };
    }

    public class KubernetesProcessor implements Processor {
        @Override
        public void process(Exchange exchange) {
            Message in = exchange.getIn();
            Event event = exchange.getIn().getBody(Event.class);
            log.info("Got event with event name: {} and action {}", event.getMetadata().getName(),
                    in.getHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION));
        }
    }
}
