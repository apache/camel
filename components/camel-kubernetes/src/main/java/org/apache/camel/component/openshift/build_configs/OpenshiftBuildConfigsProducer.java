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
package org.apache.camel.component.openshift.build_configs;

import java.util.Map;

import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigList;
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

public class OpenshiftBuildConfigsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenshiftBuildConfigsProducer.class);

    public OpenshiftBuildConfigsProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_BUILD_CONFIGS:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_BUILD_CONFIGS_BY_LABELS_OPERATION:
                doListBuildConfigsByLabels(exchange);
                break;

            case KubernetesOperations.GET_BUILD_CONFIG_OPERATION:
                doGetBuildConfig(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        BuildConfigList buildConfigsList
                = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).buildConfigs().inAnyNamespace().list();
        exchange.getMessage().setBody(buildConfigsList.getItems());
    }

    protected void doListBuildConfigsByLabels(Exchange exchange) {
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_BUILD_CONFIGS_LABELS, Map.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        BuildConfigList buildConfigsList;
        if (!ObjectHelper.isEmpty(namespaceName)) {
            buildConfigsList = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).buildConfigs()
                    .inNamespace(namespaceName).withLabels(labels).list();
        } else {
            buildConfigsList = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).buildConfigs()
                    .inAnyNamespace().withLabels(labels).list();
        }
        prepareOutboundMessage(exchange, buildConfigsList.getItems());
    }

    protected void doGetBuildConfig(Exchange exchange) {
        String buildConfigName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_BUILD_CONFIG_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(buildConfigName)) {
            LOG.error("Get a specific Build Config require specify a Build Config name");
            throw new IllegalArgumentException("Get a specific Build Config require specify a Build Config name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific Build Config require specify a namespace name");
            throw new IllegalArgumentException("Get a specific Build Config require specify a namespace name");
        }
        BuildConfig buildConfig
                = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).buildConfigs().inNamespace(namespaceName)
                        .withName(buildConfigName).get();

        prepareOutboundMessage(exchange, buildConfig);
    }
}
