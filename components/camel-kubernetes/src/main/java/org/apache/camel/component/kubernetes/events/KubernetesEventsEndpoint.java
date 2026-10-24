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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;

import static org.apache.camel.component.kubernetes.KubernetesConstants.SCHEME_EVENTS;

/**
 * Perform operations on Kubernetes Events and get notified on Events changes.
 */
@UriEndpoint(firstVersion = "3.20.0", scheme = SCHEME_EVENTS, title = "Kubernetes Event",
             syntax = "kubernetes-events:masterUrl",
             category = { Category.CONTAINER, Category.CLOUD }, headersClass = KubernetesConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO, javaType = "io.fabric8.kubernetes.api.model.events.v1.Event",
                                   description = "The Event that changed, with the action (ADDED, MODIFIED or DELETED) in the "
                                                 + "CamelKubernetesEventAction header"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "createEvent and updateEvent use an EventBuilder of fabric8 from the body, when the "
                                                + "body is one; otherwise the body is not used and the Event is built from the headers, "
                                                + "such as its name in CamelKubernetesEventName and its note in CamelKubernetesEventNote"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.POJO, Carrier.NULL, Carrier.VALUE },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "listEvents", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "listEventsByLabels",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "getEvent",
                                                          carriers = { Carrier.POJO, Carrier.NULL }),
                                           @BodyType.When(option = "operation", value = "createEvent", carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.events.v1.Event"),
                                           @BodyType.When(option = "operation", value = "updateEvent", carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.events.v1.Event"),
                                           @BodyType.When(option = "operation", value = "deleteEvent", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean") },
                                   description = "A List of the Events for the list operations, the Event of get, create and update "
                                                 + "(null when getEvent finds none), and a Boolean of whether deleteEvent deleted it"))
public class KubernetesEventsEndpoint extends AbstractKubernetesEndpoint {

    public KubernetesEventsEndpoint(String uri, KubernetesEventsComponent component, KubernetesConfiguration config) {
        super(uri, component, config);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KubernetesEventsProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = new KubernetesEventsConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;

    }

}
