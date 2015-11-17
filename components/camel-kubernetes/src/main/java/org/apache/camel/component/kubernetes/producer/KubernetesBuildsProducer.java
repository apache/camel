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
package org.apache.camel.component.kubernetes.producer;

import java.util.Map;

import io.fabric8.kubernetes.client.dsl.ClientMixedOperation;
import io.fabric8.kubernetes.client.dsl.ClientNonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.ClientOperation;
import io.fabric8.kubernetes.client.dsl.ClientResource;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildList;
import io.fabric8.openshift.api.model.DoneableBuild;
import io.fabric8.openshift.client.OpenShiftClient;

import org.apache.camel.Exchange;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesBuildsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesBuildsProducer.class);

    public KubernetesBuildsProducer(KubernetesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public KubernetesEndpoint getEndpoint() {
        return (KubernetesEndpoint) super.getEndpoint();
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
        BuildList buildList = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).builds().list();
        exchange.getOut().setBody(buildList.getItems());
    }

    protected void doListBuildByLabels(Exchange exchange, String operation) throws Exception {
        BuildList buildList = null;
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_BUILDS_LABELS,
                Map.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (!ObjectHelper.isEmpty(namespaceName)) {
            ClientNonNamespaceOperation<Build, BuildList, DoneableBuild, ClientResource<Build, DoneableBuild>> builds = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).builds()
                    .inNamespace(namespaceName);
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                builds.withLabel(entry.getKey(), entry.getValue());
            }
            buildList = builds.list();
        } else {
            ClientMixedOperation<Build, BuildList, DoneableBuild, ClientResource<Build, DoneableBuild>> builds = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).builds();
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                builds.withLabel(entry.getKey(), entry.getValue());
            }
            buildList = builds.list();
        }
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
        build = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).builds().inNamespace(namespaceName)
                .withName(buildName).get();
        exchange.getOut().setBody(build);
    }
}
