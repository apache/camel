/**
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

import java.util.Map;

import io.fabric8.kubernetes.api.model.DoneableReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.ReplicationControllerList;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListMultiDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;

import org.apache.camel.Exchange;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesOperations;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesReplicationControllersProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory
            .getLogger(KubernetesReplicationControllersProducer.class);

    public KubernetesReplicationControllersProducer(AbstractKubernetesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public AbstractKubernetesEndpoint getEndpoint() {
        return (AbstractKubernetesEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation;

        if (ObjectHelper.isEmpty(getEndpoint().getKubernetesConfiguration()
                .getOperation())) {
            operation = exchange.getIn().getHeader(
                    KubernetesConstants.KUBERNETES_OPERATION, String.class);
        } else {
            operation = getEndpoint().getKubernetesConfiguration()
                    .getOperation();
        }

        switch (operation) {

        case KubernetesOperations.LIST_REPLICATION_CONTROLLERS_OPERATION:
            doList(exchange, operation);
            break;

        case KubernetesOperations.LIST_REPLICATION_CONTROLLERS_BY_LABELS_OPERATION:
            doListReplicationControllersByLabels(exchange, operation);
            break;

        case KubernetesOperations.GET_REPLICATION_CONTROLLER_OPERATION:
            doGetReplicationController(exchange, operation);
            break;

        case KubernetesOperations.CREATE_REPLICATION_CONTROLLER_OPERATION:
            doCreateReplicationController(exchange, operation);
            break;

        case KubernetesOperations.DELETE_REPLICATION_CONTROLLER_OPERATION:
            doDeleteReplicationController(exchange, operation);
            break;
            
        case KubernetesOperations.SCALE_REPLICATION_CONTROLLER_OPERATION:
            doScaleReplicationController(exchange, operation);
            break;

        default:
            throw new IllegalArgumentException("Unsupported operation "
                    + operation);
        }
    }

    protected void doList(Exchange exchange, String operation) throws Exception {
        ReplicationControllerList rcList = null;
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (!ObjectHelper.isEmpty(namespaceName)) {
            rcList = getEndpoint().getKubernetesClient()
                    .replicationControllers().inNamespace(namespaceName).list();
        } else {
            rcList = getEndpoint().getKubernetesClient()
                    .replicationControllers().inAnyNamespace().list();
        }
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(rcList.getItems());
    }

    protected void doListReplicationControllersByLabels(Exchange exchange,
            String operation) throws Exception {
        ReplicationControllerList rcList = null;
        Map<String, String> labels = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLERS_LABELS,
                Map.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (!ObjectHelper.isEmpty(namespaceName)) {

            NonNamespaceOperation<ReplicationController, ReplicationControllerList, DoneableReplicationController, 
            RollableScalableResource<ReplicationController, DoneableReplicationController>> replicationControllers = getEndpoint().getKubernetesClient()
                    .replicationControllers().inNamespace(namespaceName);
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                replicationControllers.withLabel(entry.getKey(),
                        entry.getValue());
            }
            rcList = replicationControllers.list();
        } else {
            FilterWatchListMultiDeletable<ReplicationController, ReplicationControllerList, Boolean, Watch, Watcher<ReplicationController>> replicationControllers = getEndpoint().getKubernetesClient()
                    .replicationControllers().inAnyNamespace();
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                replicationControllers.withLabel(entry.getKey(),
                        entry.getValue());
            }
            rcList = replicationControllers.list();
        }
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(rcList.getItems());
        
    }

    protected void doGetReplicationController(Exchange exchange,
            String operation) throws Exception {
        ReplicationController rc = null;
        String rcName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME,
                String.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(rcName)) {
            LOG.error("Get a specific replication controller require specify a replication controller name");
            throw new IllegalArgumentException(
                    "Get a specific replication controller require specify a replication controller name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific replication controller require specify a namespace name");
            throw new IllegalArgumentException(
                    "Get a specific replication controller require specify a namespace name");
        }
        rc = getEndpoint().getKubernetesClient().replicationControllers()
                .inNamespace(namespaceName).withName(rcName).get();
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(rc);
    }

    protected void doCreateReplicationController(Exchange exchange,
            String operation) throws Exception {
        ReplicationController rc = null;
        String rcName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME,
                String.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        ReplicationControllerSpec rcSpec = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_SPEC,
                ReplicationControllerSpec.class);
        if (ObjectHelper.isEmpty(rcName)) {
            LOG.error("Create a specific replication controller require specify a replication controller name");
            throw new IllegalArgumentException(
                    "Create a specific replication controller require specify a replication controller name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Create a specific replication controller require specify a namespace name");
            throw new IllegalArgumentException(
                    "Create a specific replication controller require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(rcSpec)) {
            LOG.error("Create a specific replication controller require specify a replication controller spec bean");
            throw new IllegalArgumentException(
                    "Create a specific replication controller require specify a replication controller spec bean");
        }
        Map<String, String> labels = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLERS_LABELS,
                Map.class);
        ReplicationController rcCreating = new ReplicationControllerBuilder()
                .withNewMetadata().withName(rcName).withLabels(labels)
                .endMetadata().withSpec(rcSpec).build();
        rc = getEndpoint().getKubernetesClient().replicationControllers()
                .inNamespace(namespaceName).create(rcCreating);
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(rc);
    }

    protected void doDeleteReplicationController(Exchange exchange,
            String operation) throws Exception {
        String rcName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME,
                String.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(rcName)) {
            LOG.error("Delete a specific replication controller require specify a replication controller name");
            throw new IllegalArgumentException(
                    "Delete a specific replication controller require specify a replication controller name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific replication controller require specify a namespace name");
            throw new IllegalArgumentException(
                    "Delete a specific replication controller require specify a namespace name");
        }
        boolean rcDeleted = getEndpoint().getKubernetesClient()
                .replicationControllers().inNamespace(namespaceName)
                .withName(rcName).delete();
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(rcDeleted);
    }
    
    protected void doScaleReplicationController(Exchange exchange,
            String operation) throws Exception {
        String rcName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME,
                String.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        Integer replicasNumber = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_REPLICAS, Integer.class);
        if (ObjectHelper.isEmpty(rcName)) {
            LOG.error("Scale a specific replication controller require specify a replication controller name");
            throw new IllegalArgumentException(
                    "Scale a specific replication controller require specify a replication controller name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Scale a specific replication controller require specify a namespace name");
            throw new IllegalArgumentException(
                    "Scale a specific replication controller require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(replicasNumber)) {
            LOG.error("Scale a specific replication controller require specify a replicas number");
            throw new IllegalArgumentException(
                    "Scale a specific replication controller require specify a replicas number");
        }
        ReplicationController rcScaled = getEndpoint().getKubernetesClient()
                .replicationControllers().inNamespace(namespaceName)
                .withName(rcName).scale(replicasNumber, true);
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(rcScaled.getStatus().getReplicas());
    }
}
