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
package org.apache.camel.component.kubernetes.consumer;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.codec.binary.Base64;
import org.junit.Test;

public class KubernetesSecretsConsumerTest extends KubernetesTestSupport {

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    public void createAndDeleteSecrets() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }

        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_EVENT_ACTION, "ADDED",
                "DELETED");
        Exchange ex = template.request("direct:create", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SECRET_NAME, "test");
                Map<String, String> labels = new HashMap<String, String>();
                labels.put("this", "rocks");
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SECRETS_LABELS, labels);
                Secret s = new Secret();
                s.setKind("Secret");
                Map<String, String> mp = new HashMap<String, String>();
                mp.put("username", Base64.encodeBase64String("pippo".getBytes()));
                mp.put("password", Base64.encodeBase64String("password".getBytes()));
                s.setData(mp);

                ObjectMeta meta = new ObjectMeta();
                meta.setName("test");
                s.setMetadata(meta);
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SECRET, s);
            }
        });

        Secret sec = ex.getOut().getBody(Secret.class);

        assertEquals(sec.getMetadata().getName(), "test");

        ex = template.request("direct:delete", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SECRET_NAME, "test");
            }
        });

        boolean secDeleted = ex.getOut().getBody(Boolean.class);

        assertTrue(secDeleted);

        Thread.sleep(1 * 1000);

        mockResultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list").toF("kubernetes://%s?oauthToken=%s&category=secrets&operation=listSecrets", host,
                        authToken);
                from("direct:listByLabels").toF(
                        "kubernetes://%s?oauthToken=%s&category=secrets&operation=listSecretsByLabels", host,
                        authToken);
                from("direct:get").toF("kubernetes://%s?oauthToken=%s&category=secrets&operation=getSecret", host,
                        authToken);
                from("direct:create").toF("kubernetes://%s?oauthToken=%s&category=secrets&operation=createSecret", host,
                        authToken);
                from("direct:delete").toF("kubernetes://%s?oauthToken=%s&category=secrets&operation=deleteSecret", host,
                        authToken);
                fromF("kubernetes://%s?oauthToken=%s&category=secrets", host, authToken)
                        .process(new KubernertesProcessor()).to(mockResultEndpoint);
            }
        };
    }

    public class KubernertesProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Message in = exchange.getIn();
            log.info("Got event with body: " + in.getBody() + " and action "
                    + in.getHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION));
        }
    }
}
