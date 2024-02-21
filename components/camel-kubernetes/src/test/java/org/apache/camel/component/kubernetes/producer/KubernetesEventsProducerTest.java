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
package org.apache.camel.component.kubernetes.producer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.api.model.events.v1.EventBuilder;
import io.fabric8.kubernetes.api.model.events.v1.EventListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient
public class KubernetesEventsProducerTest extends KubernetesTestSupport {

    KubernetesMockServer server;
    NamespacedKubernetesClient client;

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() {
        return client;
    }

    @Test
    void listTest() {
        server.expect().withPath("/apis/events.k8s.io/v1/events")
                .andReturn(200, new EventListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        server.expect().withPath("/apis/events.k8s.io/v1/namespaces/test/events")
                .andReturn(200, new EventListBuilder().addNewItem().and().addNewItem().and().build()).once();
        List<?> result = template.requestBody("direct:list", "", List.class);
        assertEquals(3, result.size());

        Exchange ex = template.request("direct:list",
                exchange -> exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test"));
        List<?> resultNamespace = ex.getMessage().getBody(List.class);

        assertEquals(2, resultNamespace.size());
    }

    @Test
    void listByLabelsTest() throws Exception {
        server.expect().withPath("/apis/events.k8s.io/v1/events?labelSelector=" + toUrlEncoded("key1=value1,key2=value2"))
                .andReturn(200, new EventListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        Exchange ex = template.request("direct:listByLabels", exchange -> {
            Map<String, String> labels = new HashMap<>();
            labels.put("key1", "value1");
            labels.put("key2", "value2");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENTS_LABELS, labels);
        });

        List<?> result = ex.getMessage().getBody(List.class);

        assertEquals(3, result.size());
    }

    @Test
    void getEventTest() {
        Event event1 = new EventBuilder().withNewMetadata().withName("event1").withNamespace("test").and().build();
        Event event2 = new EventBuilder().withNewMetadata().withName("event2").withNamespace("ns1").and().build();

        server.expect().withPath("/apis/events.k8s.io/v1/namespaces/test/events/event1").andReturn(200, event1).once();
        server.expect().withPath("/apis/events.k8s.io/v1/namespaces/ns1/events/event2").andReturn(200, event2).once();
        Exchange ex = template.request("direct:get", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_NAME, "event1");
        });

        Event result = ex.getMessage().getBody(Event.class);

        assertEquals("event1", result.getMetadata().getName());
    }

    @Test
    void createEvent() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        String reason = "SomeReason";
        Event event1 = new EventBuilder().withNewMetadata().withName("event1").withNamespace("test").withLabels(labels).and()
                .withReason(reason).build();
        server.expect().post().withPath("/apis/events.k8s.io/v1/namespaces/test/events").andReturn(200, event1).once();

        Exchange ex = template.request("direct:create", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENTS_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_NAME, "event1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_REASON, reason);
        });

        Event result = ex.getMessage().getBody(Event.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("event1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
        assertEquals(reason, result.getReason());
    }

    @Test
    void updateEvent() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        String reason = "SomeReason";
        Event event1 = new EventBuilder().withNewMetadata().withName("event1").withNamespace("test").withLabels(labels).and()
                .withReason(reason).build();
        server.expect().get().withPath("/apis/events.k8s.io/v1/namespaces/test/events/event1")
                .andReturn(200,
                        new EventBuilder().withNewMetadata().withName("event1").withNamespace("test").endMetadata().build())
                .once();
        server.expect().put().withPath("/apis/events.k8s.io/v1/namespaces/test/events/event1").andReturn(200, event1).once();

        Exchange ex = template.request("direct:update", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENTS_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_NAME, "event1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_REASON, reason);
        });

        Event result = ex.getMessage().getBody(Event.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("event1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
        assertEquals(reason, result.getReason());
    }

    @Test
    void deleteEvent() {
        Event event1 = new EventBuilder().withNewMetadata().withName("event1").withNamespace("test").and().build();
        server.expect().withPath("/apis/events.k8s.io/v1/namespaces/test/events/event1").andReturn(200, event1).once();

        Exchange ex = template.request("direct:delete", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_EVENT_NAME, "event1");
        });

        boolean eventDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(eventDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:list").to("kubernetes-events:///?kubernetesClient=#kubernetesClient&operation=listEvents");
                from("direct:listByLabels")
                        .to("kubernetes-events:///?kubernetesClient=#kubernetesClient&operation=listEventsByLabels");
                from("direct:get").to("kubernetes-events:///?kubernetesClient=#kubernetesClient&operation=getEvent");
                from("direct:create").to("kubernetes-events:///?kubernetesClient=#kubernetesClient&operation=createEvent");
                from("direct:update").to("kubernetes-events:///?kubernetesClient=#kubernetesClient&operation=updateEvent");
                from("direct:delete").to("kubernetes-events:///?kubernetesClient=#kubernetesClient&operation=deleteEvent");
            }
        };
    }
}
