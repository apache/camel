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
package org.apache.camel.component.zookeepermaster;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.zookeeper.services.ZooKeeperService;
import org.apache.camel.test.infra.zookeeper.services.ZooKeeperServiceFactory;
import org.apache.camel.test.spring.junit6.CamelSpringTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@CamelSpringTest
@ContextConfiguration
public class MasterQuartzEndpointIT {
    @RegisterExtension
    static ZooKeeperService service = ZooKeeperServiceFactory.createSingletonService();

    @Autowired
    protected CamelContext camelContext;

    @EndpointInject("mock:results")
    protected MockEndpoint resultEndpoint;

    @Test
    public void testEndpoint() throws Exception {
        // Wait for the master election to complete before expecting messages
        MasterConsumer masterConsumer
                = (MasterConsumer) camelContext.getRoute("zookeeper-master-quartz").getConsumer();
        Awaitility.await().atMost(30, TimeUnit.SECONDS)
                .until(() -> masterConsumer.isMaster() && masterConsumer.isConnected());

        resultEndpoint.expectedMinimumMessageCount(2);
        // Allow enough time for at least 2 quartz cron fires (every 2 seconds)
        resultEndpoint.setResultWaitTime(TimeUnit.SECONDS.toMillis(30));

        MockEndpoint.assertIsSatisfied(camelContext);
    }
}
