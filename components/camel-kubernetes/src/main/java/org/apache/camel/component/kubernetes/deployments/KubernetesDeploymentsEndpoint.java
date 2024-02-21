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
package org.apache.camel.component.kubernetes.deployments;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.spi.UriEndpoint;

import static org.apache.camel.component.kubernetes.KubernetesConstants.SCHEME_DEPLOYMENTS;

/**
 * Perform operations on Kubernetes Deployments and get notified on Deployment changes.
 */
@UriEndpoint(firstVersion = "2.20.0", scheme = SCHEME_DEPLOYMENTS, title = "Kubernetes Deployments",
             syntax = "kubernetes-deployments:masterUrl", category = { Category.CONTAINER, Category.CLOUD },
             headersClass = KubernetesConstants.class)
public class KubernetesDeploymentsEndpoint extends AbstractKubernetesEndpoint {

    public KubernetesDeploymentsEndpoint(String uri, KubernetesDeploymentsComponent component, KubernetesConfiguration config) {
        super(uri, component, config);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KubernetesDeploymentsProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = new KubernetesDeploymentsConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

}
