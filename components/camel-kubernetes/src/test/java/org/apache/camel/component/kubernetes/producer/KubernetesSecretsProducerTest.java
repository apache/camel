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

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecretListBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Rule;
import org.junit.Test;

public class KubernetesSecretsProducerTest extends KubernetesTestSupport {

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
        server.expect().withPath("/api/v1/secrets").andReturn(200, new SecretListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        List<Secret> result = template.requestBody("direct:list", "", List.class);

        assertEquals(3, result.size());
    }

    @Test
    public void listByLabelsTest() throws Exception {
        server.expect().withPath("/api/v1/secrets?labelSelector=" + toUrlEncoded("key1=value1,key2=value2"))
            .andReturn(200, new SecretListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        Exchange ex = template.request("direct:listByLabels", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                Map<String, String> labels = new HashMap<String, String>();
                labels.put("key1", "value1");
                labels.put("key2", "value2");
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SECRETS_LABELS, labels);
            }
        });

        List<Secret> result = ex.getOut().getBody(List.class);

        assertEquals(3, result.size());
    }

    @Test
    public void getSecretTest() throws Exception {
        Secret sc1 = new SecretBuilder().withNewMetadata().withName("sc1").withNamespace("test").and().build();

        server.expect().withPath("/api/v1/namespaces/test/secrets/sc1").andReturn(200, sc1).once();
        Exchange ex = template.request("direct:get", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SECRET_NAME, "sc1");
            }
        });

        Secret result = ex.getOut().getBody(Secret.class);

        assertNotNull(result);
    }

    @Test
    public void createAndDeleteSecret() throws Exception {
        Secret sc1 = new SecretBuilder().withNewMetadata().withName("sc1").withNamespace("test").and().build();

        server.expect().withPath("/api/v1/namespaces/test/secrets/sc1").andReturn(200, sc1).once();
        Exchange ex = template.request("direct:delete", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SECRET_NAME, "sc1");
            }
        });

        boolean secDeleted = ex.getOut().getBody(Boolean.class);

        assertTrue(secDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list").to("kubernetes-secrets:///?kubernetesClient=#kubernetesClient&operation=listSecrets");
                from("direct:listByLabels").to("kubernetes-secrets:///?kubernetesClient=#kubernetesClient&operation=listSecretsByLabels");
                from("direct:get").to("kubernetes-secrets:///?kubernetesClient=#kubernetesClient&operation=getSecret");
                from("direct:create").to("kubernetes-secrets:///?kubernetesClient=#kubernetesClient&operation=createSecret");
                from("direct:delete").to("kubernetes-secrets:///?kubernetesClient=#kubernetesClient&operation=deleteSecret");
            }
        };
    }
}
