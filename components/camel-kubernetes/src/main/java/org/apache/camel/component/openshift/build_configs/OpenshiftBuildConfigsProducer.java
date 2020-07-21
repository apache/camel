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

import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListMultiDeletable;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigList;
import io.fabric8.openshift.api.model.DoneableBuildConfig;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.dsl.BuildConfigResource;
import org.apache.camel.Exchange;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesOperations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenshiftBuildConfigsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenshiftBuildConfigsProducer.class);

    public OpenshiftBuildConfigsProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_BUILD_CONFIGS:
                doList(exchange, operation);
                break;

            case KubernetesOperations.LIST_BUILD_CONFIGS_BY_LABELS_OPERATION:
                doListBuildConfigsByLabels(exchange, operation);
                break;

            case KubernetesOperations.GET_BUILD_CONFIG_OPERATION:
                doGetBuildConfig(exchange, operation);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange, String operation) throws Exception {
        BuildConfigList buildConfigsList = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).buildConfigs().inAnyNamespace().list();
        exchange.getOut().setBody(buildConfigsList.getItems());
    }

    protected void doListBuildConfigsByLabels(Exchange exchange, String operation) throws Exception {
        BuildConfigList buildConfigsList = null;
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_BUILD_CONFIGS_LABELS, Map.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (!ObjectHelper.isEmpty(namespaceName)) {
            NonNamespaceOperation<BuildConfig, BuildConfigList, DoneableBuildConfig, BuildConfigResource<BuildConfig, DoneableBuildConfig, Void, Build>> buildConfigs;
            buildConfigs = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).buildConfigs().inNamespace(namespaceName);
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                buildConfigs.withLabel(entry.getKey(), entry.getValue());
            }
            buildConfigsList = buildConfigs.list();
        } else {
            FilterWatchListMultiDeletable<BuildConfig, BuildConfigList, Boolean, Watch, Watcher<BuildConfig>> buildConfigs;
            buildConfigs = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).buildConfigs().inAnyNamespace();
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                buildConfigs.withLabel(entry.getKey(), entry.getValue());
            }
            buildConfigsList = buildConfigs.list();
        }
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(buildConfigsList.getItems());
    }

    protected void doGetBuildConfig(Exchange exchange, String operation) throws Exception {
        BuildConfig buildConfig = null;
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
        buildConfig = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).buildConfigs().inNamespace(namespaceName).withName(buildConfigName).get();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(buildConfig);
    }
}
