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
package org.apache.camel.component.openshift.builds;

import java.util.Map;

import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListMultiDeletable;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildList;
import io.fabric8.openshift.api.model.DoneableBuild;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.dsl.BuildResource;
import org.apache.camel.Exchange;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesOperations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenshiftBuildsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenshiftBuildsProducer.class);

    public OpenshiftBuildsProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_BUILD:
                doList(exchange, operation);
                break;

            case KubernetesOperations.LIST_BUILD_BY_LABELS_OPERATION:
                doListBuildByLabels(exchange, operation);
                break;

            case KubernetesOperations.GET_BUILD_OPERATION:
                doGetBuild(exchange, operation);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange, String operation) throws Exception {
        BuildList buildList = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).builds().inAnyNamespace().list();
        exchange.getOut().setBody(buildList.getItems());
    }

    protected void doListBuildByLabels(Exchange exchange, String operation) throws Exception {
        BuildList buildList = null;
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_BUILDS_LABELS, Map.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (!ObjectHelper.isEmpty(namespaceName)) {
            NonNamespaceOperation<Build, BuildList, DoneableBuild, BuildResource<Build, DoneableBuild, String, LogWatch>> builds = getEndpoint().getKubernetesClient()
                    .adapt(OpenShiftClient.class).builds().inNamespace(namespaceName);
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                builds.withLabel(entry.getKey(), entry.getValue());
            }
            buildList = builds.list();
        } else {
            FilterWatchListMultiDeletable<Build, BuildList, Boolean, Watch, Watcher<Build>> builds = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).builds()
                    .inAnyNamespace();
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                builds.withLabel(entry.getKey(), entry.getValue());
            }
            buildList = builds.list();
        }
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(buildList.getItems());
    }

    protected void doGetBuild(Exchange exchange, String operation) throws Exception {
        Build build = null;
        String buildName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_BUILD_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(buildName)) {
            LOG.error("Get a specific Build require specify a Build name");
            throw new IllegalArgumentException("Get a specific Build require specify a Build name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific Build require specify a namespace name");
            throw new IllegalArgumentException("Get a specific Build require specify a namespace name");
        }
        build = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).builds().inNamespace(namespaceName).withName(buildName).get();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(build);
    }
}
