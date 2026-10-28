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
package org.apache.camel.component.kubernetes.hpa;

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

import static org.apache.camel.component.kubernetes.KubernetesConstants.SCHEME_HPA;

/**
 * Perform operations on Kubernetes Horizontal Pod Autoscalers (HPA) and get notified on HPA changes.
 */
@UriEndpoint(firstVersion = "2.23.0", scheme = SCHEME_HPA, title = "Kubernetes HPA", syntax = "kubernetes-hpa:masterUrl",
             category = { Category.CONTAINER, Category.CLOUD }, headersClass = KubernetesConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO,
                                   javaType = "io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscaler",
                                   description = "The HorizontalPodAutoscaler that changed, with the action (ADDED, MODIFIED or DELETED) "
                                                 + "in the CamelKubernetesEventAction header"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Not used: the operations take their input from headers, such as the name of the "
                                                + "HorizontalPodAutoscaler in CamelKubernetesHPAName and its HorizontalPodAutoscalerSpec "
                                                + "in CamelKubernetesHPASpec"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.POJO, Carrier.NULL, Carrier.VALUE },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "listHPA", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "listHPAByLabels",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "getHPA",
                                                          carriers = { Carrier.POJO, Carrier.NULL }),
                                           @BodyType.When(option = "operation", value = "createHPA", carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscaler"),
                                           @BodyType.When(option = "operation", value = "updateHPA", carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscaler"),
                                           @BodyType.When(option = "operation", value = "deleteHPA", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean") },
                                   description = "A List of the HorizontalPodAutoscalers for the list operations, the "
                                                 + "HorizontalPodAutoscaler of get, create and update (null when getHPA finds none), and a "
                                                 + "Boolean of whether deleteHPA deleted it"))
public class KubernetesHPAEndpoint extends AbstractKubernetesEndpoint {

    public KubernetesHPAEndpoint(String uri, KubernetesHPAComponent component, KubernetesConfiguration config) {
        super(uri, component, config);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KubernetesHPAProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = new KubernetesHPAConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;

    }

}
