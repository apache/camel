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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.extensions.DeploymentListBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Rule;
import org.junit.Test;

public class KubernetesDeploymentsProducerTest extends KubernetesTestSupport {

    @Rule
    public KubernetesServer server = new KubernetesServer();

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("kubernetesClient", server.getClient());
        return registry;
    }
    
    @Test
    public void listTest() throws Exception {
        server.expect().withPath("/apis/extensions/v1beta1/namespaces/test/deployments").andReturn(200, new DeploymentListBuilder().addNewItem().and().build()).once();
        List<Deployment> result = template.requestBody("direct:list", "",
                List.class);

        assertEquals(1, result.size());
    }

    @Test
    public void listByLabelsTest() throws Exception {
        server.expect().withPath("/apis/extensions/v1beta1/namespaces/test/deployments?labelSelector=" + toUrlEncoded("key1=value1,key2=value2"))
        .andReturn(200, new DeploymentListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        Exchange ex = template.request("direct:listByLabels", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                Map<String, String> labels = new HashMap<String, String>();
                labels.put("key1", "value1");
                labels.put("key2", "value2");
                exchange.getIn()
                        .setHeader(
                                KubernetesConstants.KUBERNETES_DEPLOYMENTS_LABELS,
                                labels);
            }
        });

        List<Deployment> result = ex.getOut().getBody(List.class);
        
        assertEquals(3, result.size());
    }

    @Test
    public void createAndDeleteDeployment() throws Exception {
        Deployment de1 = new DeploymentBuilder().withNewMetadata()
            .withNamespace("test")
            .withName("de1")
            .withResourceVersion("1")
            .withGeneration(2L)
            .endMetadata()
            .withNewSpec()
            .withReplicas(0)
            .endSpec()
            .withNewStatus()
            .withReplicas(1)
            .withObservedGeneration(1L)
            .endStatus()
            .build();
        
        server.expect().withPath("/apis/extensions/v1beta1/namespaces/test/deployments/de1").andReturn(200, de1).once();
        server.expect().withPath("/apis/extensions/v1beta1/namespaces/test/deployments/de1").andReturn(200, new DeploymentBuilder(de1)
                                                                                                              .editStatus()
                                                                                                              .withReplicas(0)
                                                                                                              .withObservedGeneration(2L)
                                                                                                              .endStatus()
                                                                                                              .build()).times(5);

        Exchange ex = template.request("direct:deleteDeployment", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, "de1");
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            }
        });

        boolean deDeleted = ex.getOut().getBody(Boolean.class);

        assertTrue(deDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list")
                        .toF("kubernetes-deployments:///?kubernetesClient=#kubernetesClient&operation=listDeployments");
                from("direct:listByLabels")
                        .toF("kubernetes-deployments:///?kubernetesClient=#kubernetesClient&operation=listDeploymentsByLabels");
                from("direct:deleteDeployment")
                        .toF("kubernetes-deployments:///?kubernetesClient=#kubernetesClient&operation=deleteDeployment");
                from("direct:createDeployment")
                        .toF("kubernetes-deployments:///?kubernetesClient=#kubernetesClient&operation=createDeployment");
            }
        };
    }
}
