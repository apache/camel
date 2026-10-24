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
package org.apache.camel.component.kubernetes.nodes;

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

import static org.apache.camel.component.kubernetes.KubernetesConstants.SCHEME_NODES;

/**
 * Perform operations on Kubernetes Nodes and get notified on Node changes.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = SCHEME_NODES, title = "Kubernetes Nodes",
             syntax = "kubernetes-nodes:masterUrl", category = { Category.CONTAINER, Category.CLOUD },
             headersClass = KubernetesConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO, javaType = "io.fabric8.kubernetes.api.model.Node",
                                   description = "The Node that changed, with the action (ADDED, MODIFIED or DELETED) in the "
                                                 + "CamelKubernetesEventAction header"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Not used: the operations take their input from headers, such as the name of the Node "
                                                + "in CamelKubernetesNodeName and its NodeSpec in CamelKubernetesNodeSpec"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.POJO, Carrier.NULL, Carrier.VALUE },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "listNodes", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "listNodesByLabels",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "getNode",
                                                          carriers = { Carrier.POJO, Carrier.NULL }),
                                           @BodyType.When(option = "operation", value = "createNode", carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.Node"),
                                           @BodyType.When(option = "operation", value = "updateNode", carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.Node"),
                                           @BodyType.When(option = "operation", value = "deleteNode", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean") },
                                   description = "A List of the Nodes for the list operations, the Node of get, create and update (null "
                                                 + "when getNode finds none), and a Boolean of whether deleteNode deleted it"))
public class KubernetesNodesEndpoint extends AbstractKubernetesEndpoint {

    public KubernetesNodesEndpoint(String uri, KubernetesNodesComponent component, KubernetesConfiguration config) {
        super(uri, component, config);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KubernetesNodesProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = new KubernetesNodesConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;

    }

}
