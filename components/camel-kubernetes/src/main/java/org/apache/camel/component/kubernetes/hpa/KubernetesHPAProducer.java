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

import java.util.Map;

import io.fabric8.kubernetes.api.model.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscalerBuilder;
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscalerList;
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscalerSpec;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListMultiDeletable;
import org.apache.camel.Exchange;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesOperations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesHPAProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesHPAProducer.class);

    public KubernetesHPAProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_HPA:
                doList(exchange, operation);
                break;

            case KubernetesOperations.LIST_HPA_BY_LABELS_OPERATION:
                doListHPAByLabel(exchange, operation);
                break;

            case KubernetesOperations.GET_HPA_OPERATION:
                doGetHPA(exchange, operation);
                break;

            case KubernetesOperations.CREATE_HPA_OPERATION:
                doCreateHPA(exchange, operation);
                break;

            case KubernetesOperations.DELETE_HPA_OPERATION:
                doDeleteHPA(exchange, operation);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange, String operation) throws Exception {
        HorizontalPodAutoscalerList hpaList = getEndpoint().getKubernetesClient().autoscaling().horizontalPodAutoscalers().list();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(hpaList.getItems());
    }

    protected void doListHPAByLabel(Exchange exchange, String operation) {
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_HPA_LABELS, Map.class);
        if (ObjectHelper.isEmpty(labels)) {
            LOG.error("Get HPA by labels require specify a labels set");
            throw new IllegalArgumentException("Get HPA by labels require specify a labels set");
        }

        FilterWatchListMultiDeletable<HorizontalPodAutoscaler, HorizontalPodAutoscalerList, Boolean, Watch, Watcher<HorizontalPodAutoscaler>> hpas = getEndpoint()
                .getKubernetesClient().autoscaling().horizontalPodAutoscalers();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            hpas.withLabel(entry.getKey(), entry.getValue());
        }
        HorizontalPodAutoscalerList hpaList = hpas.list();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(hpaList.getItems());
    }

    protected void doGetHPA(Exchange exchange, String operation) throws Exception {
        HorizontalPodAutoscaler hpa = null;
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
        hpa = getEndpoint().getKubernetesClient().autoscaling().horizontalPodAutoscalers().inNamespace(namespaceName).withName(podName).get();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(hpa);
    }

    protected void doCreateHPA(Exchange exchange, String operation) throws Exception {
        HorizontalPodAutoscaler hpa = null;
        String hpaName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_HPA_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        HorizontalPodAutoscalerSpec hpaSpec = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_HPA_SPEC, HorizontalPodAutoscalerSpec.class);
        if (ObjectHelper.isEmpty(hpaName)) {
            LOG.error("Create a specific hpa require specify a hpa name");
            throw new IllegalArgumentException("Create a specific hpa require specify a hpa name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Create a specific hpa require specify a namespace name");
            throw new IllegalArgumentException("Create a specific hpa require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(hpaSpec)) {
            LOG.error("Create a specific hpa require specify a hpa spec bean");
            throw new IllegalArgumentException("Create a specific hpa require specify a hpa spec bean");
        }
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_HPA_LABELS, Map.class);
        HorizontalPodAutoscaler hpaCreating = new HorizontalPodAutoscalerBuilder().withNewMetadata().withName(hpaName).withLabels(labels).endMetadata().withSpec(hpaSpec).build();
        hpa = getEndpoint().getKubernetesClient().autoscaling().horizontalPodAutoscalers().inNamespace(namespaceName).create(hpaCreating);

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(hpa);
    }

    protected void doDeleteHPA(Exchange exchange, String operation) throws Exception {
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
        boolean hpaDeleted = getEndpoint().getKubernetesClient().autoscaling().horizontalPodAutoscalers().inNamespace(namespaceName).withName(hpaName).delete();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(hpaDeleted);
    }
}
