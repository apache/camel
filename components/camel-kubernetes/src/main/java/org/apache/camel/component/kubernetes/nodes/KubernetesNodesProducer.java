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

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.NodeSpec;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.client.dsl.Resource;
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

public class KubernetesNodesProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesNodesProducer.class);

    public KubernetesNodesProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_NODES:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_NODES_BY_LABELS_OPERATION:
                doListNodesByLabels(exchange);
                break;

            case KubernetesOperations.GET_NODE_OPERATION:
                doGetNode(exchange);
                break;

            case KubernetesOperations.CREATE_NODE_OPERATION:
                doCreateNode(exchange);
                break;

            case KubernetesOperations.UPDATE_NODE_OPERATION:
                doUpdateNode(exchange);
                break;

            case KubernetesOperations.DELETE_NODE_OPERATION:
                doDeleteNode(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        NodeList nodeList = getEndpoint().getKubernetesClient().nodes().list();

        prepareOutboundMessage(exchange, nodeList.getItems());
    }

    protected void doListNodesByLabels(Exchange exchange) {
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NODES_LABELS, Map.class);
        NodeList nodeList = getEndpoint().getKubernetesClient()
                .nodes()
                .withLabels(labels)
                .list();

        prepareOutboundMessage(exchange, nodeList.getItems());
    }

    protected void doGetNode(Exchange exchange) {
        String pvName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NODE_NAME, String.class);
        if (ObjectHelper.isEmpty(pvName)) {
            LOG.error("Get a specific Node require specify a Node name");
            throw new IllegalArgumentException("Get a specific Node require specify a Node name");
        }
        Node node = getEndpoint().getKubernetesClient().nodes().withName(pvName).get();

        prepareOutboundMessage(exchange, node);
    }

    protected void doUpdateNode(Exchange exchange) {
        doCreateOrUpdateNode(exchange, "Update", Resource::update);
    }

    protected void doCreateNode(Exchange exchange) {
        doCreateOrUpdateNode(exchange, "Create", Resource::create);
    }

    private void doCreateOrUpdateNode(Exchange exchange, String operationName, Function<Resource<Node>, Node> operation) {
        String nodeName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NODE_NAME, String.class);
        NodeSpec nodeSpec = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NODE_SPEC, NodeSpec.class);
        if (ObjectHelper.isEmpty(nodeName)) {
            LOG.error("{} a specific node require specify a node name", operationName);
            throw new IllegalArgumentException(String.format("%s a specific node require specify a node name", operationName));
        }
        if (ObjectHelper.isEmpty(nodeSpec)) {
            LOG.error("{} a specific node require specify a node spec bean", operationName);
            throw new IllegalArgumentException(
                    String.format("%s a specific node require specify a node spec bean", operationName));
        }
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_PODS_LABELS, Map.class);
        Node nodeCreating = new NodeBuilder().withNewMetadata().withName(nodeName).withLabels(labels).endMetadata()
                .withSpec(nodeSpec).build();
        Node node = operation.apply(getEndpoint().getKubernetesClient().nodes().resource(nodeCreating));

        prepareOutboundMessage(exchange, node);
    }

    protected void doDeleteNode(Exchange exchange) {
        String nodeName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NODE_NAME, String.class);
        if (ObjectHelper.isEmpty(nodeName)) {
            LOG.error("Deleting a specific Node require specify a Node name");
            throw new IllegalArgumentException("Deleting a specific Node require specify a Node name");
        }

        List<StatusDetails> statusDetails = getEndpoint().getKubernetesClient().nodes().withName(nodeName).delete();
        boolean nodeDeleted = ObjectHelper.isNotEmpty(statusDetails);

        prepareOutboundMessage(exchange, nodeDeleted);
    }
}
