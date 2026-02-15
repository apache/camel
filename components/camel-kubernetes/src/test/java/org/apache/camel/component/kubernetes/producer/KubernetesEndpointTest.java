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
package org.apache.camel.component.kubernetes.producer;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.component.kubernetes.pods.KubernetesPodsComponent;
import org.apache.camel.component.kubernetes.pods.KubernetesPodsEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KubernetesEndpointTest extends CamelTestSupport {
    @BindToRegistry
    KubernetesClient client = new KubernetesClientBuilder().build();

    @Test
    void endpointStopDoesNotCloseAutowiredKubernetesClient() {
        assertFalse(client.getHttpClient().isClosed());
        KubernetesPodsEndpoint endpoint
                = context.getEndpoint("kubernetes-pods:local?operation=listPods", KubernetesPodsEndpoint.class);
        endpoint.stop();
        assertFalse(client.getHttpClient().isClosed());
    }

    @Test
    void endpointStopClosesNonAutowiredKubernetesClient() {
        KubernetesPodsEndpoint endpoint
                = context.getEndpoint("kubernetes-no-autowired-pods:local?operation=listPods", KubernetesPodsEndpoint.class);
        assertFalse(endpoint.getKubernetesClient().getHttpClient().isClosed());
        endpoint.stop();
        assertTrue(endpoint.getKubernetesClient().getHttpClient().isClosed());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        KubernetesPodsComponent podsComponent = new KubernetesPodsComponent();
        podsComponent.setAutowiredEnabled(false);
        camelContext.addComponent("kubernetes-no-autowired-pods", podsComponent);
        return camelContext;
    }
}
