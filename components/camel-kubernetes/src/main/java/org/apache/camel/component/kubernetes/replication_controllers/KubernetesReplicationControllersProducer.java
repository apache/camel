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

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.ReplicationControllerList;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
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

public class KubernetesReplicationControllersProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesReplicationControllersProducer.class);

    public KubernetesReplicationControllersProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_REPLICATION_CONTROLLERS_OPERATION:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_REPLICATION_CONTROLLERS_BY_LABELS_OPERATION:
                doListReplicationControllersByLabels(exchange);
                break;

            case KubernetesOperations.GET_REPLICATION_CONTROLLER_OPERATION:
                doGetReplicationController(exchange);
                break;

            case KubernetesOperations.CREATE_REPLICATION_CONTROLLER_OPERATION:
                doCreateReplicationController(exchange);
                break;

            case KubernetesOperations.UPDATE_REPLICATION_CONTROLLER_OPERATION:
                doUpdateReplicationController(exchange);
                break;

            case KubernetesOperations.DELETE_REPLICATION_CONTROLLER_OPERATION:
                doDeleteReplicationController(exchange);
                break;

            case KubernetesOperations.SCALE_REPLICATION_CONTROLLER_OPERATION:
                doScaleReplicationController(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        ReplicationControllerList rcList;
        if (!ObjectHelper.isEmpty(namespaceName)) {
            rcList = getEndpoint().getKubernetesClient().replicationControllers().inNamespace(namespaceName).list();
        } else {
            rcList = getEndpoint().getKubernetesClient().replicationControllers().inAnyNamespace().list();
        }

        prepareOutboundMessage(exchange, rcList.getItems());
    }

    protected void doListReplicationControllersByLabels(Exchange exchange) {
        Map<String, String> labels
                = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLERS_LABELS, Map.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        ReplicationControllerList rcList;
        if (!ObjectHelper.isEmpty(namespaceName)) {

            NonNamespaceOperation<ReplicationController, ReplicationControllerList, RollableScalableResource<ReplicationController>> replicationControllers
                    = getEndpoint()
                            .getKubernetesClient().replicationControllers().inNamespace(namespaceName);

            rcList = replicationControllers.withLabels(labels).list();
        } else {
            rcList = getEndpoint().getKubernetesClient()
                    .replicationControllers()
                    .inAnyNamespace()
                    .withLabels(labels)
                    .list();
        }

        prepareOutboundMessage(exchange, rcList.getItems());

    }

    protected void doGetReplicationController(Exchange exchange) {
        String rcName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(rcName)) {
            LOG.error("Get a specific replication controller require specify a replication controller name");
            throw new IllegalArgumentException(
                    "Get a specific replication controller require specify a replication controller name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific replication controller require specify a namespace name");
            throw new IllegalArgumentException("Get a specific replication controller require specify a namespace name");
        }
        ReplicationController rc = getEndpoint().getKubernetesClient().replicationControllers().inNamespace(namespaceName)
                .withName(rcName).get();

        prepareOutboundMessage(exchange, rc);
    }

    protected void doUpdateReplicationController(Exchange exchange) {
        doCreateOrUpdateReplicationController(exchange, "Update", Resource::update);
    }

    protected void doCreateReplicationController(Exchange exchange) {
        doCreateOrUpdateReplicationController(exchange, "Create", Resource::create);
    }

    private void doCreateOrUpdateReplicationController(
            Exchange exchange, String operationName,
            Function<Resource<ReplicationController>, ReplicationController> operation) {
        String rcName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        ReplicationControllerSpec rcSpec = exchange.getIn()
                .getHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_SPEC, ReplicationControllerSpec.class);
        if (ObjectHelper.isEmpty(rcName)) {
            LOG.error("{} a specific replication controller require specify a replication controller name", operationName);
            throw new IllegalArgumentException(
                    String.format("%s a specific replication controller require specify a replication controller name",
                            operationName));
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("{} a specific replication controller require specify a namespace name", operationName);
            throw new IllegalArgumentException(
                    String.format("%s a specific replication controller require specify a namespace name", operationName));
        }
        if (ObjectHelper.isEmpty(rcSpec)) {
            LOG.error("{} a specific replication controller require specify a replication controller spec bean", operationName);
            throw new IllegalArgumentException(
                    String.format("%s a specific replication controller require specify a replication controller spec bean",
                            operationName));
        }
        Map<String, String> labels
                = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLERS_LABELS, Map.class);
        ReplicationController rcCreating = new ReplicationControllerBuilder().withNewMetadata().withName(rcName)
                .withLabels(labels).endMetadata().withSpec(rcSpec).build();
        ReplicationController rc
                = operation.apply(getEndpoint().getKubernetesClient().replicationControllers().inNamespace(namespaceName)
                        .resource(rcCreating));

        prepareOutboundMessage(exchange, rc);
    }

    protected void doDeleteReplicationController(Exchange exchange) {
        String rcName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(rcName)) {
            LOG.error("Delete a specific replication controller require specify a replication controller name");
            throw new IllegalArgumentException(
                    "Delete a specific replication controller require specify a replication controller name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific replication controller require specify a namespace name");
            throw new IllegalArgumentException("Delete a specific replication controller require specify a namespace name");
        }

        List<StatusDetails> statusDetails
                = getEndpoint().getKubernetesClient().replicationControllers().inNamespace(namespaceName)
                        .withName(rcName).delete();
        boolean rcDeleted = ObjectHelper.isNotEmpty(statusDetails);

        prepareOutboundMessage(exchange, rcDeleted);
    }

    protected void doScaleReplicationController(Exchange exchange) {
        String rcName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        Integer replicasNumber
                = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_REPLICAS, Integer.class);
        if (ObjectHelper.isEmpty(rcName)) {
            LOG.error("Scale a specific replication controller require specify a replication controller name");
            throw new IllegalArgumentException(
                    "Scale a specific replication controller require specify a replication controller name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Scale a specific replication controller require specify a namespace name");
            throw new IllegalArgumentException("Scale a specific replication controller require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(replicasNumber)) {
            LOG.error("Scale a specific replication controller require specify a replicas number");
            throw new IllegalArgumentException("Scale a specific replication controller require specify a replicas number");
        }
        ReplicationController rcScaled = getEndpoint().getKubernetesClient().replicationControllers().inNamespace(namespaceName)
                .withName(rcName).scale(replicasNumber, false);

        prepareOutboundMessage(exchange, rcScaled.getStatus().getReplicas());
    }
}
