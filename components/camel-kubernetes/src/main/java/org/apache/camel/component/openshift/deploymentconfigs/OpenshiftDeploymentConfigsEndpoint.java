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
package org.apache.camel.component.openshift.deploymentconfigs;

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

import static org.apache.camel.component.kubernetes.KubernetesConstants.SCHEME_DEPLOYMENT_CONFIGS;

/**
 * Perform operations on OpenShift Deployment Configs and get notified on Deployment Config changes.
 */
@UriEndpoint(firstVersion = "3.18.0", scheme = SCHEME_DEPLOYMENT_CONFIGS, title = "OpenShift Deployment Configs",
             syntax = "openshift-deploymentconfigs:masterUrl", category = { Category.CONTAINER, Category.CLOUD },
             headersClass = KubernetesConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO, javaType = "io.fabric8.openshift.api.model.DeploymentConfig",
                                   description = "The DeploymentConfig that changed, one message per watch event, with the"
                                                 + " action (ADDED, MODIFIED, DELETED) in the CamelKubernetesEventAction"
                                                 + " header"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Not used: the operation (the operation option, or else the"
                                                + " CamelKubernetesOperation header) takes its input from headers: the"
                                                + " namespace in CamelKubernetesNamespaceName, the name in"
                                                + " CamelKubernetesDeploymentName, the labels in"
                                                + " CamelKubernetesDeploymentsLabels, the DeploymentConfigSpec in"
                                                + " CamelKubernetesDeploymentConfigSpec and the replicas in"
                                                + " CamelKubernetesDeploymentReplicas. The list and get operations do not read the namespace"
                                                + " header: they use the namespace of the client"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.POJO, Carrier.NULL, Carrier.VALUE },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "listDeploymentConfigs",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<io.fabric8.openshift.api.model.DeploymentConfig>"),
                                           @BodyType.When(option = "operation", value = "listDeploymentConfigsByLabels",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<io.fabric8.openshift.api.model.DeploymentConfig>"),
                                           @BodyType.When(option = "operation", value = "getDeploymentConfig",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "io.fabric8.openshift.api.model.DeploymentConfig"),
                                           @BodyType.When(option = "operation", value = "createDeploymentConfig",
                                                          carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.openshift.api.model.DeploymentConfig"),
                                           @BodyType.When(option = "operation", value = "updateDeploymentConfig",
                                                          carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.openshift.api.model.DeploymentConfig"),
                                           @BodyType.When(option = "operation", value = "deleteDeploymentConfig",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "scaleDeploymentConfig",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Integer") },
                                   description = "A List of DeploymentConfig for listDeploymentConfigs and"
                                                 + " listDeploymentConfigsByLabels; the DeploymentConfig for"
                                                 + " getDeploymentConfig (null when not found), createDeploymentConfig and"
                                                 + " updateDeploymentConfig; a Boolean for deleteDeploymentConfig, false when"
                                                 + " not found; for scaleDeploymentConfig the replicas of its status, as an"
                                                 + " Integer"))
public class OpenshiftDeploymentConfigsEndpoint extends AbstractKubernetesEndpoint {

    public OpenshiftDeploymentConfigsEndpoint(String uri, OpenshiftDeploymentConfigsComponent component,
                                              KubernetesConfiguration config) {
        super(uri, component, config);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new OpenshiftDeploymentConfigsProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = new OpenshiftDeploymentConfigsConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

}
