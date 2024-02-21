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
package org.apache.camel.component.kubernetes.hpa;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscalerBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscalerList;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscalerSpec;
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

public class KubernetesHPAProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesHPAProducer.class);

    public KubernetesHPAProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_HPA:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_HPA_BY_LABELS_OPERATION:
                doListHPAByLabel(exchange);
                break;

            case KubernetesOperations.GET_HPA_OPERATION:
                doGetHPA(exchange);
                break;

            case KubernetesOperations.CREATE_HPA_OPERATION:
                doCreateHPA(exchange);
                break;

            case KubernetesOperations.UPDATE_HPA_OPERATION:
                doUpdateHPA(exchange);
                break;

            case KubernetesOperations.DELETE_HPA_OPERATION:
                doDeleteHPA(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        HorizontalPodAutoscalerList hpaList
                = getEndpoint().getKubernetesClient().autoscaling().v1().horizontalPodAutoscalers().list();

        prepareOutboundMessage(exchange, hpaList.getItems());
    }

    protected void doListHPAByLabel(Exchange exchange) {
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_HPA_LABELS, Map.class);
        if (ObjectHelper.isEmpty(labels)) {
            LOG.error("Get HPA by labels require specify a labels set");
            throw new IllegalArgumentException("Get HPA by labels require specify a labels set");
        }

        HorizontalPodAutoscalerList hpaList = getEndpoint()
                .getKubernetesClient()
                .autoscaling()
                .v1()
                .horizontalPodAutoscalers()
                .withLabels(labels)
                .list();

        prepareOutboundMessage(exchange, hpaList.getItems());
    }

    protected void doGetHPA(Exchange exchange) {
        String podName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_HPA_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(podName)) {
            LOG.error("Get a specific hpa require specify an hpa name");
            throw new IllegalArgumentException("Get a specific hpa require specify an hpa name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific hpa require specify a namespace name");
            throw new IllegalArgumentException("Get a specific hpa require specify a namespace name");
        }
        HorizontalPodAutoscaler hpa
                = getEndpoint().getKubernetesClient().autoscaling().v1().horizontalPodAutoscalers().inNamespace(namespaceName)
                        .withName(podName).get();

        prepareOutboundMessage(exchange, hpa);
    }

    protected void doUpdateHPA(Exchange exchange) {
        doCreateOrUpdateHPA(exchange, "Update", Resource::update);
    }

    protected void doCreateHPA(Exchange exchange) {
        doCreateOrUpdateHPA(exchange, "Create", Resource::create);
    }

    private void doCreateOrUpdateHPA(
            Exchange exchange, String operationName,
            Function<Resource<HorizontalPodAutoscaler>, HorizontalPodAutoscaler> operation) {
        String hpaName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_HPA_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        HorizontalPodAutoscalerSpec hpaSpec
                = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_HPA_SPEC, HorizontalPodAutoscalerSpec.class);
        if (ObjectHelper.isEmpty(hpaName)) {
            LOG.error("{} a specific hpa require specify a hpa name", operationName);
            throw new IllegalArgumentException(operationName + " a specific hpa require specify a hpa name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("{} a specific hpa require specify a namespace name", operationName);
            throw new IllegalArgumentException(operationName + " a specific hpa require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(hpaSpec)) {
            LOG.error("{} a specific hpa require specify a hpa spec bean", operationName);
            throw new IllegalArgumentException(operationName + " a specific hpa require specify a hpa spec bean");
        }
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_HPA_LABELS, Map.class);
        HorizontalPodAutoscaler hpaCreating = new HorizontalPodAutoscalerBuilder().withNewMetadata().withName(hpaName)
                .withLabels(labels).endMetadata().withSpec(hpaSpec).build();
        HorizontalPodAutoscaler hpa
                = operation.apply(getEndpoint().getKubernetesClient().autoscaling().v1().horizontalPodAutoscalers()
                        .inNamespace(namespaceName)
                        .resource(hpaCreating));

        prepareOutboundMessage(exchange, hpa);
    }

    protected void doDeleteHPA(Exchange exchange) {
        String hpaName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_HPA_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(hpaName)) {
            LOG.error("Delete a specific hpa require specify a hpa name");
            throw new IllegalArgumentException("Delete a specific hpa require specify a hpa name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific hpa require specify a namespace name");
            throw new IllegalArgumentException("Delete a specific hpa require specify a namespace name");
        }

        List<StatusDetails> statusDetails = getEndpoint().getKubernetesClient().autoscaling().v1().horizontalPodAutoscalers()
                .inNamespace(namespaceName).withName(hpaName).delete();
        boolean hpaDeleted = ObjectHelper.isNotEmpty(statusDetails);

        prepareOutboundMessage(exchange, hpaDeleted);
    }
}
