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
package org.apache.camel.component.zookeeper.springboot.cluster;

import java.io.File;
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.component.zookeeper.cluster.ZooKeeperClusterService;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.curator.test.TestingServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class ZooKeeperClusterServiceTest {
    private static final String SERVICE_PATH = "/camel";

    @Rule
    public final TestName testName = new TestName();
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testClusterService() throws Exception {
        final int zkPort =  AvailablePortFinder.getNextAvailable();
        final File zkDir =  temporaryFolder.newFolder();

        final TestingServer zkServer = new TestingServer(zkPort, zkDir);
        zkServer.start();

        try {
            new ApplicationContextRunner()
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                    "debug=false",
                    "spring.main.banner-mode=OFF",
                    "spring.application.name=" + UUID.randomUUID().toString(),
                    "camel.component.zookeeper.cluster.service.enabled=true",
                    "camel.component.zookeeper.cluster.service.nodes=localhost:" + zkPort,
                    "camel.component.zookeeper.cluster.service.id=" + UUID.randomUUID().toString(),
                    "camel.component.zookeeper.cluster.service.base-path=" + SERVICE_PATH)
                .run(
                    context -> {
                        assertThat(context).hasSingleBean(CamelContext.class);
                        assertThat(context).hasSingleBean(CamelClusterService.class);

                        final CamelContext camelContext = context.getBean(CamelContext.class);
                        final CamelClusterService clusterService = camelContext.hasService(CamelClusterService.class);

                        assertThat(clusterService).isNotNull();
                        assertThat(clusterService).isInstanceOf(ZooKeeperClusterService.class);
                    }
                );
        } finally {
            zkServer.stop();
        }
    }

    // *************************************
    // Config
    // *************************************

    @EnableAutoConfiguration
    @Configuration
    public static class TestConfiguration {
    }
}
