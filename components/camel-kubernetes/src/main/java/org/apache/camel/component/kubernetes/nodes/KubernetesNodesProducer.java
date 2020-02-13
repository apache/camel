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

import java.util.Map;

import io.fabric8.kubernetes.api.model.DoneableNode;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.NodeSpec;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.apache.camel.Exchange;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesOperations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesNodesProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesNodesProducer.class);

    public KubernetesNodesProducer(AbstractKubernetesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public AbstractKubernetesEndpoint getEndpoint() {
        return (AbstractKubernetesEndpoint)super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation;

        if (ObjectHelper.isEmpty(getEndpoint().getKubernetesConfiguration().getOperation())) {
            operation = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_OPERATION, String.class);
        } else {
            operation = getEndpoint().getKubernetesConfiguration().getOperation();
        }

        switch (operation) {

            case KubernetesOperations.LIST_NODES:
                doList(exchange, operation);
                break;

            case KubernetesOperations.LIST_NODES_BY_LABELS_OPERATION:
                doListNodesByLabels(exchange, operation);
                break;

            case KubernetesOperations.GET_NODE_OPERATION:
                doGetNode(exchange, operation);
                break;

            case KubernetesOperations.CREATE_NODE_OPERATION:
                doCreateNode(exchange, operation);
                break;

            case KubernetesOperations.DELETE_NODE_OPERATION:
                doDeleteNode(exchange, operation);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange, String operation) throws Exception {
        NodeList nodeList = getEndpoint().getKubernetesClient().nodes().list();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(nodeList.getItems());
    }

    protected void doListNodesByLabels(Exchange exchange, String operation) throws Exception {
        NodeList nodeList = null;
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NODES_LABELS, Map.class);
        NonNamespaceOperation<Node, NodeList, DoneableNode, Resource<Node, DoneableNode>> nodes = getEndpoint().getKubernetesClient().nodes();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            nodes.withLabel(entry.getKey(), entry.getValue());
        }
        nodeList = nodes.list();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(nodeList.getItems());
    }

    protected void doGetNode(Exchange exchange, String operation) throws Exception {
        Node node = null;
        String pvName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NODE_NAME, String.class);
        if (ObjectHelper.isEmpty(pvName)) {
            LOG.error("Get a specific Node require specify a Node name");
            throw new IllegalArgumentException("Get a specific Node require specify a Node name");
        }
        node = getEndpoint().getKubernetesClient().nodes().withName(pvName).get();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(node);
    }

    protected void doCreateNode(Exchange exchange, String operation) throws Exception {
        Node node = null;
        String nodeName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NODE_NAME, String.class);
        NodeSpec nodeSpec = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NODE_SPEC, NodeSpec.class);
        if (ObjectHelper.isEmpty(nodeName)) {
            LOG.error("Create a specific node require specify a node name");
            throw new IllegalArgumentException("Create a specific node require specify a node name");
        }
        if (ObjectHelper.isEmpty(nodeSpec)) {
            LOG.error("Create a specific node require specify a node spec bean");
            throw new IllegalArgumentException("Create a specific node require specify a node spec bean");
        }
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_PODS_LABELS, Map.class);
        Node nodeCreating = new NodeBuilder().withNewMetadata().withName(nodeName).withLabels(labels).endMetadata().withSpec(nodeSpec).build();
        node = getEndpoint().getKubernetesClient().nodes().create(nodeCreating);

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(node);
    }

    protected void doDeleteNode(Exchange exchange, String operation) throws Exception {
        String nodeName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NODE_NAME, String.class);
        if (ObjectHelper.isEmpty(nodeName)) {
            LOG.error("Deleting a specific Node require specify a Node name");
            throw new IllegalArgumentException("Deleting a specific Node require specify a Node name");
        }
        boolean nodeDeleted = getEndpoint().getKubernetesClient().nodes().withName(nodeName).delete();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(nodeDeleted);
    }
}
