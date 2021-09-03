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
package org.apache.camel.component.kubernetes.job;

import java.util.Map;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobList;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.ScalableResource;
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

public class KubernetesJobProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesJobProducer.class);

    public KubernetesJobProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_JOB:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_JOB_BY_LABELS_OPERATION:
                doListJobByLabel(exchange);
                break;

            case KubernetesOperations.GET_JOB_OPERATION:
                doGetJob(exchange);
                break;

            case KubernetesOperations.CREATE_JOB_OPERATION:
                doCreateJob(exchange);
                break;

            case KubernetesOperations.DELETE_JOB_OPERATION:
                doDeleteJob(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        JobList jobList = getEndpoint().getKubernetesClient().batch().v1().jobs().list();

        prepareOutboundMessage(exchange, jobList.getItems());
    }

    protected void doListJobByLabel(Exchange exchange) {
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_JOB_LABELS, Map.class);
        if (ObjectHelper.isEmpty(labels)) {
            LOG.error("Get Job by labels require specify a labels set");
            throw new IllegalArgumentException("Get Job by labels require specify a labels set");
        }

        MixedOperation<Job, JobList, ScalableResource<Job>> jobs = getEndpoint().getKubernetesClient().batch().v1().jobs();

        JobList jobList = jobs.withLabels(labels).list();

        prepareOutboundMessage(exchange, jobList.getItems());
    }

    protected void doGetJob(Exchange exchange) {
        Job job = null;
        String jobName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_JOB_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(jobName)) {
            LOG.error("Get a specific job require specify a job name");
            throw new IllegalArgumentException("Get a specific job require specify a job name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific job require specify a namespace name");
            throw new IllegalArgumentException("Get a specific job require specify a namespace name");
        }
        job = getEndpoint().getKubernetesClient().batch().v1().jobs().inNamespace(namespaceName).withName(jobName).get();

        prepareOutboundMessage(exchange, job);
    }

    protected void doCreateJob(Exchange exchange) {
        Job job = null;
        String jobName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_JOB_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        JobSpec jobSpec = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_JOB_SPEC, JobSpec.class);
        if (ObjectHelper.isEmpty(jobName)) {
            LOG.error("Create a specific job require specify a job name");
            throw new IllegalArgumentException("Create a specific job require specify a job name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Create a specific job require specify a namespace name");
            throw new IllegalArgumentException("Create a specific job require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(jobSpec)) {
            LOG.error("Create a specific job require specify a hpa spec bean");
            throw new IllegalArgumentException("Create a specific job require specify a hpa spec bean");
        }
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_JOB_LABELS, Map.class);
        Job jobCreating = new JobBuilder().withNewMetadata().withName(jobName).withLabels(labels).endMetadata()
                .withSpec(jobSpec).build();
        job = getEndpoint().getKubernetesClient().batch().v1().jobs().inNamespace(namespaceName).create(jobCreating);

        prepareOutboundMessage(exchange, job);
    }

    protected void doDeleteJob(Exchange exchange) {
        String jobName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_JOB_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(jobName)) {
            LOG.error("Delete a specific job require specify a job name");
            throw new IllegalArgumentException("Delete a specific job require specify a job name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific job require specify a namespace name");
            throw new IllegalArgumentException("Delete a specific job require specify a namespace name");
        }

        getEndpoint().getKubernetesClient().batch().v1().jobs().inNamespace(namespaceName).withName(jobName).delete();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getMessage(), true);
    }
}
