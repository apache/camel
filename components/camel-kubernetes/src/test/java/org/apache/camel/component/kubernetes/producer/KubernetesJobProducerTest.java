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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.api.model.batch.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.JobListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.junit.Rule;
import org.junit.Test;

public class KubernetesJobProducerTest extends KubernetesTestSupport {

    @Rule
    public KubernetesServer server = new KubernetesServer();

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() throws Exception {
        return server.getClient();
    }

    @Test
    public void listTest() throws Exception {
        server.expect().withPath("/apis/batch/v1/namespaces/test/jobs").andReturn(200, new JobListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build())
            .once();
        List<Secret> result = template.requestBody("direct:list", "", List.class);

        assertEquals(3, result.size());
    }

    @Test
    public void listByLabelsTest() throws Exception {
        server.expect().withPath("/apis/batch/v1/namespaces/test/jobs?labelSelector=" + toUrlEncoded("key1=value1,key2=value2"))
            .andReturn(200, new JobListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        Exchange ex = template.request("direct:listByLabels", exchange -> {
            Map<String, String> labels = new HashMap<>();
            labels.put("key1", "value1");
            labels.put("key2", "value2");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_JOB_LABELS, labels);
        });

        List<Job> result = ex.getMessage().getBody(List.class);

        assertEquals(3, result.size());
    }

    @Test
    public void getJobTest() throws Exception {
        Job sc1 = new JobBuilder().withNewMetadata().withName("sc1").withNamespace("test").and().build();

        server.expect().withPath("/apis/batch/v1/namespaces/test/jobs/sc1").andReturn(200, sc1).once();
        Exchange ex = template.request("direct:get", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_JOB_NAME, "sc1");
        });

        Job result = ex.getMessage().getBody(Job.class);

        assertNotNull(result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list").to("kubernetes-job:///?kubernetesClient=#kubernetesClient&operation=listJob");
                from("direct:listByLabels").to("kubernetes-job:///?kubernetesClient=#kubernetesClient&operation=listJobByLabels");
                from("direct:get").to("kubernetes-job:///?kubernetesClient=#kubernetesClient&operation=getJob");
                from("direct:create").to("kubernetes-job:///?kubernetesClient=#kubernetesClient&operation=createJob");
                from("direct:delete").to("kubernetes-job:///?kubernetesClient=#kubernetesClient&operation=deleteJob");
            }
        };
    }
}
