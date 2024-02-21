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

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnableKubernetesMockClient
public class KubernetesCronJobProducerTest extends KubernetesTestSupport {

    KubernetesMockServer server;
    NamespacedKubernetesClient client;

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() {
        return client;
    }

    @Test
    void listTest() {
        server.expect().withPath("/apis/batch/v1/namespaces/test/cronjobs")
                .andReturn(200, new CronJobListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build())
                .once();
        List<?> result = template.requestBody("direct:listCronJob", "", List.class);

        assertEquals(3, result.size());
    }

    @Test
    void listByLabelsTest() throws Exception {
        server.expect()
                .withPath("/apis/batch/v1/namespaces/test/cronjobs?labelSelector=" + toUrlEncoded("key1=value1,key2=value2"))
                .andReturn(200, new CronJobListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build())
                .once();
        Exchange ex = template.request("direct:listByLabels", exchange -> {
            Map<String, String> labels = new HashMap<>();
            labels.put("key1", "value1");
            labels.put("key2", "value2");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRON_JOB_LABELS, labels);
        });

        List<?> result = ex.getMessage().getBody(List.class);

        assertEquals(3, result.size());
    }

    @Test
    void getJobTest() {
        CronJob sc1 = new CronJobBuilder().withNewMetadata().withName("sc1").withNamespace("test").and().build();

        server.expect().withPath("/apis/batch/v1/namespaces/test/cronjobs/sc1").andReturn(200, sc1).once();
        Exchange ex = template.request("direct:get", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRON_JOB_NAME, "sc1");
        });

        CronJob result = ex.getMessage().getBody(CronJob.class);

        assertNotNull(result);
    }

    @Test
    void createJobTest() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        CronJobSpec spec = new CronJobSpecBuilder().build();
        CronJob j1 = new CronJobBuilder().withNewMetadata().withName("j1").withNamespace("test").withLabels(labels).and()
                .withSpec(spec).build();
        server.expect().post().withPath("/apis/batch/v1/namespaces/test/cronjobs").andReturn(200, j1).once();

        Exchange ex = template.request("direct:create", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRON_JOB_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRON_JOB_NAME, "j1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRON_JOB_SPEC, spec);
        });

        CronJob result = ex.getMessage().getBody(CronJob.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("j1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
    }

    @Test
    void updateJobTest() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        CronJobSpec spec = new CronJobSpecBuilder().withJobTemplate(new JobTemplateSpecBuilder().build()).build();
        CronJob j1 = new CronJobBuilder().withNewMetadata().withName("j1").withNamespace("test").withLabels(labels).and()
                .withSpec(spec).build();
        server.expect().get().withPath("/apis/batch/v1/namespaces/test/cronjobs/j1")
                .andReturn(200, new JobBuilder().withNewMetadata().withName("j1").withNamespace("test").endMetadata()
                        .withSpec(new JobSpecBuilder()
                                .withTemplate(new PodTemplateSpecBuilder().withMetadata(new ObjectMeta()).build()).build())
                        .build())
                .times(2);
        server.expect().put().withPath("/apis/batch/v1/namespaces/test/cronjobs/j1").andReturn(200, j1).once();

        Exchange ex = template.request("direct:update", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRON_JOB_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRON_JOB_NAME, "j1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRON_JOB_SPEC, spec);
        });

        CronJob result = ex.getMessage().getBody(CronJob.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("j1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
    }

    @Test
    void deleteJobTest() {
        CronJob j1 = new CronJobBuilder().withNewMetadata().withName("j1").withNamespace("test").and().build();
        server.expect().delete().withPath("/apis/batch/v1/namespaces/test/cronjobs/j1").andReturn(200, j1)
                .once();

        Exchange ex = template.request("direct:delete", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRON_JOB_NAME, "j1");
        });

        assertNotNull(ex.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the kubernetes-client is autowired on the component

                from("direct:listCronJob").to("kubernetes-cronjob:foo?operation=listCronJob");
                from("direct:listByLabels").to("kubernetes-cronjob:foo?operation=listCronJobByLabels");
                from("direct:get").to("kubernetes-cronjob:foo?operation=getCronJob");
                from("direct:create").to("kubernetes-cronjob:foo?operation=createCronJob");
                from("direct:update").to("kubernetes-cronjob:foo?operation=updateCronJob");
                from("direct:delete").to("kubernetes-cronjob:foo?operation=deleteCronJob");
            }
        };
    }
}
