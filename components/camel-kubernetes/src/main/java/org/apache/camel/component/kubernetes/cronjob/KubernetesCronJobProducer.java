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
package org.apache.camel.component.kubernetes.cronjob;

import java.util.Map;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.batch.v1.*;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
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

import static org.apache.camel.component.kubernetes.KubernetesHelper.prepareOutboundMessage;

public class KubernetesCronJobProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesCronJobProducer.class);

    public KubernetesCronJobProducer(AbstractKubernetesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public AbstractKubernetesEndpoint getEndpoint() {
        return (AbstractKubernetesEndpoint) super.getEndpoint();
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

            case KubernetesOperations.LIST_CRON_JOB_OPERATION:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_CRON_JOB_BY_LABELS_OPERATION:
                doListCronJobByLabel(exchange);
                break;

            case KubernetesOperations.GET_CRON_JOB_OPERATION:
                doGetCronJob(exchange);
                break;

            case KubernetesOperations.CREATE_CRON_JOB_OPERATION:
                doCreateCronJob(exchange);
                break;

            case KubernetesOperations.UPDATE_CRON_JOB_OPERATION:
                doUpdateCronJob(exchange);
                break;

            case KubernetesOperations.DELETE_CRON_JOB_OPERATION:
                doDeleteCronJob(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        CronJobList cronJobList = getEndpoint().getKubernetesClient().batch().v1().cronjobs().list();

        prepareOutboundMessage(exchange, cronJobList.getItems());
    }

    protected void doListCronJobByLabel(Exchange exchange) {
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CRON_JOB_LABELS, Map.class);
        if (ObjectHelper.isEmpty(labels)) {
            throw new IllegalArgumentException("Cron Job by labels require specify a labels set");
        }

        MixedOperation<CronJob, CronJobList, Resource<CronJob>> cronJobs
                = getEndpoint().getKubernetesClient().batch().v1().cronjobs();

        CronJobList jobList = cronJobs.withLabels(labels).list();

        prepareOutboundMessage(exchange, jobList.getItems());
    }

    protected void doGetCronJob(Exchange exchange) {
        String cronjobName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CRON_JOB_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(cronjobName)) {
            throw new IllegalArgumentException("Get a specific cronjob require specify a cronnjob name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            throw new IllegalArgumentException("Get a specific cronjob require specify a namespace name");
        }
        CronJob cronJob = getEndpoint().getKubernetesClient().batch().v1().cronjobs().inNamespace(namespaceName)
                .withName(cronjobName).get();

        prepareOutboundMessage(exchange, cronJob);
    }

    protected void doUpdateCronJob(Exchange exchange) {
        doCreateOrUpdateCronJob(exchange, "Update", Resource::update);
    }

    protected void doCreateCronJob(Exchange exchange) {
        doCreateOrUpdateCronJob(exchange, "Create", Resource::create);
    }

    private void doCreateOrUpdateCronJob(
            Exchange exchange, String operationName, Function<Resource<CronJob>, Object> operation) {
        String cronjobName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CRON_JOB_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        CronJobSpec cronjobSpec = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CRON_JOB_SPEC, CronJobSpec.class);
        if (ObjectHelper.isEmpty(cronjobName)) {
            throw new IllegalArgumentException(
                    String.format("%s a specific cronjob require specify a cronjob name", operationName));
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            throw new IllegalArgumentException(
                    String.format("%s a specific cronjob require specify a namespace name", operationName));
        }
        if (ObjectHelper.isEmpty(cronjobSpec)) {
            throw new IllegalArgumentException(
                    String.format("%s a specific cronjob require specify a cronjob spec bean", operationName));
        }
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CRON_JOB_LABELS, Map.class);
        CronJob cronjobCreating = new CronJobBuilder().withNewMetadata().withName(cronjobName).withLabels(labels).endMetadata()
                .withSpec(cronjobSpec).build();
        Object cronJob = operation.apply(
                getEndpoint().getKubernetesClient().batch().v1().cronjobs().inNamespace(namespaceName)
                        .resource(cronjobCreating));

        prepareOutboundMessage(exchange, cronJob);
    }

    protected void doDeleteCronJob(Exchange exchange) {
        String cronjobName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CRON_JOB_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(cronjobName)) {
            throw new IllegalArgumentException("Delete a specific cronjob require specify a cronjob name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            throw new IllegalArgumentException("Delete a specific cronjob require specify a namespace name");
        }

        getEndpoint().getKubernetesClient().batch().v1().cronjobs().inNamespace(namespaceName).withName(cronjobName).delete();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getMessage(), true);
    }
}
