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

import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.api.model.batch.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.JobList;
import io.fabric8.kubernetes.api.model.batch.JobSpec;
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

public class KubernetesJobProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesJobProducer.class);

    public KubernetesJobProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_JOB:
                doList(exchange, operation);
                break;

            case KubernetesOperations.LIST_JOB_BY_LABELS_OPERATION:
                doListJobByLabel(exchange, operation);
                break;

            case KubernetesOperations.GET_JOB_OPERATION:
                doGetJob(exchange, operation);
                break;

            case KubernetesOperations.CREATE_JOB_OPERATION:
                doCreateJob(exchange, operation);
                break;

            case KubernetesOperations.DELETE_JOB_OPERATION:
                doDeleteJob(exchange, operation);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange, String operation) throws Exception {
        JobList jobList = getEndpoint().getKubernetesClient().batch().jobs().list();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(jobList.getItems());
    }

    protected void doListJobByLabel(Exchange exchange, String operation) {
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_JOB_LABELS, Map.class);
        if (ObjectHelper.isEmpty(labels)) {
            LOG.error("Get Job by labels require specify a labels set");
            throw new IllegalArgumentException("Get Job by labels require specify a labels set");
        }

        FilterWatchListMultiDeletable<Job, JobList, Boolean, Watch, Watcher<Job>> jobs = getEndpoint().getKubernetesClient().batch().jobs();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            jobs.withLabel(entry.getKey(), entry.getValue());
        }
        JobList jobList = jobs.list();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(jobList.getItems());
    }

    protected void doGetJob(Exchange exchange, String operation) throws Exception {
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
        job = getEndpoint().getKubernetesClient().batch().jobs().inNamespace(namespaceName).withName(jobName).get();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(job);
    }

    protected void doCreateJob(Exchange exchange, String operation) throws Exception {
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
        Job jobCreating = new JobBuilder().withNewMetadata().withName(jobName).withLabels(labels).endMetadata().withSpec(jobSpec).build();
        job = getEndpoint().getKubernetesClient().batch().jobs().inNamespace(namespaceName).create(jobCreating);

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(job);
    }

    protected void doDeleteJob(Exchange exchange, String operation) throws Exception {
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

        getEndpoint().getKubernetesClient().batch().jobs().inNamespace(namespaceName).withName(jobName).delete();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
    }
}
