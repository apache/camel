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
package org.apache.camel.component.kubernetes.springboot.test.cluster;

import org.apache.camel.component.kubernetes.cluster.KubernetesClusterService;
import org.apache.camel.component.kubernetes.springboot.cluster.KubernetesClusterServiceAutoConfiguration;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class KubernetesClusterServiceAutoConfigurationTest {

    @Test
    public void testDisable() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KubernetesClusterServiceAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .run(
                context -> {
                    assertThat(context).doesNotHaveBean(KubernetesClusterService.class);
                }
            );
    }

    /**
     * Testing that the service can be enabled and configured completely.
     */
    @Test
    public void testPropertiesMapped() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KubernetesClusterServiceAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                "spring.main.banner-mode=off",
                "camel.component.kubernetes.cluster.service.enabled=true",
                "camel.component.kubernetes.cluster.service.id=myid1",
                "camel.component.kubernetes.cluster.service.master-url=http://myurl:9000",
                "camel.component.kubernetes.cluster.service.connection-timeout-millis=1234",
                "camel.component.kubernetes.cluster.service.kubernetes-namespace=ns1",
                "camel.component.kubernetes.cluster.service.config-map-name=cm",
                "camel.component.kubernetes.cluster.service.pod-name=mypod1",
                "camel.component.kubernetes.cluster.service.cluster-labels.app=myapp",
                "camel.component.kubernetes.cluster.service.cluster-labels.provider=myprovider",
                "camel.component.kubernetes.cluster.service.lease-duration-millis=10000",
                "camel.component.kubernetes.cluster.service.renew-deadline-millis=8000",
                "camel.component.kubernetes.cluster.service.retry-period-millis=4000")
            .run(
                context -> {
                    final KubernetesClusterService clusterService = context.getBean(KubernetesClusterService.class);

                    assertEquals("myid1", clusterService.getId());
                    assertEquals("http://myurl:9000", clusterService.getMasterUrl());
                    assertEquals(Integer.valueOf(1234), clusterService.getConnectionTimeoutMillis());
                    assertEquals("ns1", clusterService.getKubernetesNamespace());
                    assertEquals("cm", clusterService.getConfigMapName());
                    assertEquals("mypod1", clusterService.getPodName());

                    assertNotNull(clusterService.getClusterLabels());
                    assertEquals(2, clusterService.getClusterLabels().size());
                    assertEquals("myapp", clusterService.getClusterLabels().get("app"));
                    assertEquals("myprovider", clusterService.getClusterLabels().get("provider"));

                    assertEquals(1.2, clusterService.getJitterFactor(), 1e-10);
                    assertEquals(10000, clusterService.getLeaseDurationMillis());
                    assertEquals(8000, clusterService.getRenewDeadlineMillis());
                    assertEquals(4000, clusterService.getRetryPeriodMillis());
                }
            );
    }

    @EnableAutoConfiguration
    @Configuration
    public static class TestConfiguration {
    }
}

