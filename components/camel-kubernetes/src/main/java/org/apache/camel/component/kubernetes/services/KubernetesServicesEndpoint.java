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
package org.apache.camel.component.kubernetes.services;

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

import static org.apache.camel.component.kubernetes.KubernetesConstants.SCHEME_SERVICES;

/**
 * Perform operations on Kubernetes Services and get notified on Service changes.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = SCHEME_SERVICES, title = "Kubernetes Services",
             syntax = "kubernetes-services:masterUrl", category = { Category.CONTAINER, Category.CLOUD },
             headersClass = KubernetesConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO, javaType = "io.fabric8.kubernetes.api.model.Service",
                                   description = "The Service that changed, one message per watch event, with the action"
                                                 + " (ADDED, MODIFIED, DELETED) in the CamelKubernetesEventAction header"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Not used: the operation (the operation option, or else the"
                                                + " CamelKubernetesOperation header) takes its input from headers: the"
                                                + " namespace in CamelKubernetesNamespaceName, the name in"
                                                + " CamelKubernetesServiceName, the labels in CamelKubernetesServiceLabels"
                                                + " and the ServiceSpec in CamelKubernetesServiceSpec"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.POJO, Carrier.NULL, Carrier.VALUE },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "listServices", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<io.fabric8.kubernetes.api.model.Service>"),
                                           @BodyType.When(option = "operation", value = "listServicesByLabels",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<io.fabric8.kubernetes.api.model.Service>"),
                                           @BodyType.When(option = "operation", value = "getService",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "io.fabric8.kubernetes.api.model.Service"),
                                           @BodyType.When(option = "operation", value = "createService",
                                                          carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.Service"),
                                           @BodyType.When(option = "operation", value = "updateService",
                                                          carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.Service"),
                                           @BodyType.When(option = "operation", value = "deleteService",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean") },
                                   description = "A List of Service for listServices and listServicesByLabels; the Service"
                                                 + " for getService (null when not found), createService and updateService; a"
                                                 + " Boolean for deleteService, false when not found"))
public class KubernetesServicesEndpoint extends AbstractKubernetesEndpoint {

    public KubernetesServicesEndpoint(String uri, KubernetesServicesComponent component, KubernetesConfiguration config) {
        super(uri, component, config);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KubernetesServicesProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = new KubernetesServicesConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;

    }

}
