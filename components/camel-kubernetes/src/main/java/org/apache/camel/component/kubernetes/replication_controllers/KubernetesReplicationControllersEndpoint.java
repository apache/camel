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
package org.apache.camel.component.kubernetes.replication_controllers;

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

import static org.apache.camel.component.kubernetes.KubernetesConstants.SCHEME_REPLICATION_CONTROLLERS;

/**
 * Perform operations on Kubernetes Replication Controllers and get notified on Replication Controllers changes.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = SCHEME_REPLICATION_CONTROLLERS,
             title = "Kubernetes Replication Controller", syntax = "kubernetes-replication-controllers:masterUrl",
             category = { Category.CONTAINER, Category.CLOUD }, headersClass = KubernetesConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO, javaType = "io.fabric8.kubernetes.api.model.ReplicationController",
                                   description = "The ReplicationController that changed, one message per watch event, with"
                                                 + " the action (ADDED, MODIFIED, DELETED) in the CamelKubernetesEventAction"
                                                 + " header"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Not used: the operation (the operation option, or else the"
                                                + " CamelKubernetesOperation header) takes its input from headers: the"
                                                + " namespace in CamelKubernetesNamespaceName, the name in"
                                                + " CamelKubernetesReplicationControllerName, the labels in"
                                                + " CamelKubernetesReplicationControllersLabels, the"
                                                + " ReplicationControllerSpec in CamelKubernetesReplicationControllerSpec and"
                                                + " the replicas in CamelKubernetesReplicationControllerReplicas"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.POJO, Carrier.NULL, Carrier.VALUE },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "listReplicationControllers",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<io.fabric8.kubernetes.api.model.ReplicationController>"),
                                           @BodyType.When(option = "operation", value = "listReplicationControllersByLabels",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<io.fabric8.kubernetes.api.model.ReplicationController>"),
                                           @BodyType.When(option = "operation", value = "getReplicationController",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "io.fabric8.kubernetes.api.model.ReplicationController"),
                                           @BodyType.When(option = "operation", value = "createReplicationController",
                                                          carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.ReplicationController"),
                                           @BodyType.When(option = "operation", value = "updateReplicationController",
                                                          carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.ReplicationController"),
                                           @BodyType.When(option = "operation", value = "deleteReplicationController",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "scaleReplicationController",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Integer") },
                                   description = "A List of ReplicationController for listReplicationControllers and"
                                                 + " listReplicationControllersByLabels; the ReplicationController for"
                                                 + " getReplicationController (null when not found),"
                                                 + " createReplicationController and updateReplicationController; a Boolean"
                                                 + " for deleteReplicationController, false when not found; for"
                                                 + " scaleReplicationController the replicas of its status, as an Integer"))
public class KubernetesReplicationControllersEndpoint extends AbstractKubernetesEndpoint {

    public KubernetesReplicationControllersEndpoint(String uri, KubernetesReplicationControllersComponent component,
                                                    KubernetesConfiguration config) {
        super(uri, component, config);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KubernetesReplicationControllersProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = new KubernetesReplicationControllersConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

}
