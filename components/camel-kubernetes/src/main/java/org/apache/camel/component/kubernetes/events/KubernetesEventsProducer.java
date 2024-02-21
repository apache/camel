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
package org.apache.camel.component.kubernetes.events;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.MicroTimeBuilder;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.api.model.events.v1.EventBuilder;
import io.fabric8.kubernetes.api.model.events.v1.EventList;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.apache.camel.Exchange;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesHelper;
import org.apache.camel.component.kubernetes.KubernetesOperations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.kubernetes.KubernetesHelper.prepareOutboundMessage;

public class KubernetesEventsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesEventsProducer.class);

    public KubernetesEventsProducer(AbstractKubernetesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public AbstractKubernetesEndpoint getEndpoint() {
        return (AbstractKubernetesEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation = KubernetesHelper.extractOperation(getEndpoint(), exchange);

        switch (operation) {

            case KubernetesOperations.LIST_EVENTS_OPERATION:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_EVENTS_BY_LABELS_OPERATION:
                doListEventsByLabel(exchange);
                break;

            case KubernetesOperations.GET_EVENT_OPERATION:
                doGetEvent(exchange);
                break;

            case KubernetesOperations.CREATE_EVENT_OPERATION:
                doCreateEvent(exchange);
                break;

            case KubernetesOperations.UPDATE_EVENT_OPERATION:
                doUpdateEvent(exchange);
                break;

            case KubernetesOperations.DELETE_EVENT_OPERATION:
                doDeleteEvent(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        EventList eventList;
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isNotEmpty(namespaceName)) {
            eventList = getEndpoint().getKubernetesClient().events().v1().events().inNamespace(namespaceName).list();
        } else {
            eventList = getEndpoint().getKubernetesClient().events().v1().events().inAnyNamespace().list();
        }
        prepareOutboundMessage(exchange, eventList.getItems());
    }

    protected void doListEventsByLabel(Exchange exchange) {
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_EVENTS_LABELS, Map.class);
        if (ObjectHelper.isEmpty(labels)) {
            LOG.error("Get events by labels require specify a labels set");
            throw new IllegalArgumentException("Get events by labels require specify a labels set");
        }

        EventList eventList = getEndpoint().getKubernetesClient()
                .events()
                .v1()
                .events()
                .inAnyNamespace()
                .withLabels(labels)
                .list();

        prepareOutboundMessage(exchange, eventList.getItems());
    }

    protected void doGetEvent(Exchange exchange) {
        String eventName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_EVENT_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(eventName)) {
            LOG.error("Get a specific event require specify a event name");
            throw new IllegalArgumentException("Get a specific event require specify a event name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific event require specify a namespace name");
            throw new IllegalArgumentException("Get a specific event require specify a namespace name");
        }
        Event event = getEndpoint().getKubernetesClient().events().v1().events().inNamespace(namespaceName).withName(eventName)
                .get();

        prepareOutboundMessage(exchange, event);
    }

    protected void doUpdateEvent(Exchange exchange) {
        doCreateOrUpdateEvent(exchange, "Update", Resource::update);
    }

    protected void doCreateEvent(Exchange exchange) {
        doCreateOrUpdateEvent(exchange, "Create", Resource::create);
    }

    private void doCreateOrUpdateEvent(Exchange exchange, String operationName, Function<Resource<Event>, Event> operation) {
        String eventName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_EVENT_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(eventName)) {
            LOG.error("{} a specific event require specify a event name", operationName);
            throw new IllegalArgumentException(
                    String.format("%s a specific event require specify a event name", operationName));
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("{} a specific event require specify a namespace name", operationName);
            throw new IllegalArgumentException(
                    String.format("%s a specific event require specify a namespace name", operationName));
        }
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_EVENTS_LABELS, Map.class);
        EventBuilder builder = exchange.getIn().getBody(EventBuilder.class);
        if (builder == null) {
            builder = new EventBuilder()
                    .withEventTime(new MicroTimeBuilder().withTime(
                            exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_EVENT_TIME,
                                    () -> OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), String.class))
                            .build())
                    .withAction(exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION_PRODUCER, String.class))
                    .withType(exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_EVENT_TYPE, String.class))
                    .withReason(exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_EVENT_REASON, String.class))
                    .withNote(exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_EVENT_NOTE, String.class))
                    .withReportingController(
                            exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_EVENT_REPORTING_CONTROLLER, String.class))
                    .withReportingInstance(
                            exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_EVENT_REPORTING_INSTANCE, String.class))
                    .withRegarding(
                            exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_EVENT_REGARDING, ObjectReference.class))
                    .withRelated(
                            exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_EVENT_RELATED, ObjectReference.class));
        }
        Event eventCreating = builder.withNewMetadata().withName(eventName).withLabels(labels).endMetadata()
                .build();
        Event event = operation.apply(
                getEndpoint().getKubernetesClient().events().v1().events().inNamespace(namespaceName).resource(eventCreating));

        prepareOutboundMessage(exchange, event);
    }

    protected void doDeleteEvent(Exchange exchange) {
        String eventName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_EVENT_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(eventName)) {
            LOG.error("Delete a specific event require specify a event name");
            throw new IllegalArgumentException("Delete a specific event require specify a event name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific event require specify a namespace name");
            throw new IllegalArgumentException("Delete a specific event require specify a namespace name");
        }

        List<StatusDetails> statusDetails
                = getEndpoint().getKubernetesClient().events().v1().events().inNamespace(namespaceName).withName(eventName)
                        .delete();
        boolean eventDeleted = ObjectHelper.isNotEmpty(statusDetails);

        prepareOutboundMessage(exchange, eventDeleted);
    }
}
