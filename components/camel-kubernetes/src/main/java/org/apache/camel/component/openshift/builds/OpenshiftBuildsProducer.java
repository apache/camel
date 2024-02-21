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

import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildList;
import io.fabric8.openshift.client.OpenShiftClient;
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

public class OpenshiftBuildsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenshiftBuildsProducer.class);

    public OpenshiftBuildsProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_BUILD:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_BUILD_BY_LABELS_OPERATION:
                doListBuildByLabels(exchange);
                break;

            case KubernetesOperations.GET_BUILD_OPERATION:
                doGetBuild(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        BuildList buildList = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).builds().inAnyNamespace().list();
        exchange.getMessage().setBody(buildList.getItems());
    }

    protected void doListBuildByLabels(Exchange exchange) {
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_BUILDS_LABELS, Map.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        BuildList buildList;
        if (!ObjectHelper.isEmpty(namespaceName)) {
            buildList = getEndpoint().getKubernetesClient()
                    .adapt(OpenShiftClient.class).builds().inNamespace(namespaceName).withLabels(labels).list();
        } else {
            buildList = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).builds()
                    .inAnyNamespace().withLabels(labels).list();
        }

        prepareOutboundMessage(exchange, buildList.getItems());
    }

    protected void doGetBuild(Exchange exchange) {
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
        Build build = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).builds().inNamespace(namespaceName)
                .withName(buildName).get();

        prepareOutboundMessage(exchange, build);
    }
}
