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
package org.apache.camel.component.kubernetes.deployments;

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

import static org.apache.camel.component.kubernetes.KubernetesConstants.SCHEME_DEPLOYMENTS;

/**
 * Perform operations on Kubernetes Deployments and get notified on Deployment changes.
 */
@UriEndpoint(firstVersion = "2.20.0", scheme = SCHEME_DEPLOYMENTS, title = "Kubernetes Deployments",
             syntax = "kubernetes-deployments:masterUrl", category = { Category.CONTAINER, Category.CLOUD },
             headersClass = KubernetesConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO, javaType = "io.fabric8.kubernetes.api.model.apps.Deployment",
                                   description = "The Deployment that changed, with the action (ADDED, MODIFIED or DELETED) in the "
                                                 + "CamelKubernetesEventAction header"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Not used: the operations take their input from headers, such as the name of the "
                                                + "Deployment in CamelKubernetesDeploymentName, its DeploymentSpec in "
                                                + "CamelKubernetesDeploymentSpec and the replicas of scaleDeployment in "
                                                + "CamelKubernetesDeploymentReplicas"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.POJO, Carrier.NULL, Carrier.VALUE },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "listDeployments",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "listDeploymentsByLabels",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "getDeployment",
                                                          carriers = { Carrier.POJO, Carrier.NULL }),
                                           @BodyType.When(option = "operation", value = "createDeployment",
                                                          carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.apps.Deployment"),
                                           @BodyType.When(option = "operation", value = "updateDeployment",
                                                          carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.apps.Deployment"),
                                           @BodyType.When(option = "operation", value = "deleteDeployment",
                                                          carriers = Carrier.VALUE, javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "scaleDeployment",
                                                          carriers = Carrier.VALUE, javaType = "java.lang.Integer") },
                                   description = "A List of the Deployments for the list operations, the Deployment of get, create and "
                                                 + "update (null when getDeployment finds none), and a Boolean of whether deleteDeployment "
                                                 + "deleted it. scaleDeployment gives the number of replicas in the status of the "
                                                 + "Deployment"))
public class KubernetesDeploymentsEndpoint extends AbstractKubernetesEndpoint {

    public KubernetesDeploymentsEndpoint(String uri, KubernetesDeploymentsComponent component, KubernetesConfiguration config) {
        super(uri, component, config);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KubernetesDeploymentsProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = new KubernetesDeploymentsConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

}
