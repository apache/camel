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
package org.apache.camel.component.kubernetes.namespaces;

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

import static org.apache.camel.component.kubernetes.KubernetesConstants.SCHEME_NAMESPACES;

/**
 * Perform operations on Kubernetes Namespaces and get notified on Namespace changes.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = SCHEME_NAMESPACES, title = "Kubernetes Namespaces",
             syntax = "kubernetes-namespaces:masterUrl", category = { Category.CONTAINER, Category.CLOUD },
             headersClass = KubernetesConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO, javaType = "io.fabric8.kubernetes.api.model.Namespace",
                                   description = "The Namespace that changed, with the action (ADDED, MODIFIED or DELETED) in the "
                                                 + "CamelKubernetesEventAction header"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Not used: the operations take their input from headers, such as the name of the "
                                                + "Namespace in CamelKubernetesNamespaceName and its labels as a Map in "
                                                + "CamelKubernetesNamespaceLabels"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.POJO, Carrier.NULL, Carrier.VALUE },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "listNamespaces",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "listNamespacesByLabels",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "getNamespace",
                                                          carriers = { Carrier.POJO, Carrier.NULL }),
                                           @BodyType.When(option = "operation", value = "createNamespace",
                                                          carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.Namespace"),
                                           @BodyType.When(option = "operation", value = "updateNamespace",
                                                          carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.Namespace"),
                                           @BodyType.When(option = "operation", value = "deleteNamespace",
                                                          carriers = Carrier.VALUE, javaType = "java.lang.Boolean") },
                                   description = "A List of the Namespaces for the list operations, the Namespace of get, create and "
                                                 + "update (null when getNamespace finds none), and a Boolean of whether deleteNamespace "
                                                 + "deleted it"))
public class KubernetesNamespacesEndpoint extends AbstractKubernetesEndpoint {

    public KubernetesNamespacesEndpoint(String uri, KubernetesNamespacesComponent component, KubernetesConfiguration config) {
        super(uri, component, config);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KubernetesNamespacesProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = new KubernetesNamespacesConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;

    }

}
