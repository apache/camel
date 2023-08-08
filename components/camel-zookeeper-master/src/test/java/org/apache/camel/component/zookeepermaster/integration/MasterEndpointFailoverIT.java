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
package org.apache.camel.component.zookeepermaster.integration;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.seda.SedaComponent;
import org.apache.camel.component.zookeepermaster.CuratorFactoryBean;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.infra.zookeeper.services.ZooKeeperService;
import org.apache.camel.test.infra.zookeeper.services.ZooKeeperServiceFactory;
import org.apache.curator.framework.CuratorFramework;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MasterEndpointFailoverIT {
    @RegisterExtension
    static ZooKeeperService service = ZooKeeperServiceFactory.createService();

    private static final transient Logger LOG = LoggerFactory.getLogger(MasterEndpointFailoverIT.class);

    protected ProducerTemplate template;
    protected CamelContext producerContext;
    protected CamelContext consumerContext1;
    protected CamelContext consumerContext2;
    protected MockEndpoint result1Endpoint;
    protected MockEndpoint result2Endpoint;
    protected AtomicInteger messageCounter = new AtomicInteger(1);
    protected CuratorFactoryBean zkClientBean = new CuratorFactoryBean();

    @BeforeEach
    public void beforeRun() throws Exception {
        // Create the zkClientBean
        zkClientBean.setConnectString(service.getConnectionString());
        CuratorFramework client = zkClientBean.getObject();

        // Need to bind the zookeeper client with the name "curator"
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("curator", client);

        producerContext = new DefaultCamelContext(registry);
        // Add the seda:start endpoint to avoid the NPE before starting the consumerContext1
        producerContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("seda:start");
            }
        });
        SedaComponent sedaComponent = new SedaComponent();
        producerContext.addComponent("seda", sedaComponent);

        template = producerContext.createProducerTemplate();

        consumerContext1 = new DefaultCamelContext(registry);
        consumerContext1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("zookeeper-master:MasterEndpointFailoverTest:seda:start")
                        .to("log:result1")
                        .to("mock:result1");
            }
        });
        consumerContext1.addComponent("seda", sedaComponent);
        consumerContext2 = new DefaultCamelContext(registry);
        consumerContext2.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("zookeeper-master:MasterEndpointFailoverTest:seda:start")
                        .to("log:result2")
                        .to("mock:result2");
            }
        });
        consumerContext2.addComponent("seda", sedaComponent);
        // Need to start at less one consumerContext to enable the seda queue for producerContext
        producerContext.start();
        consumerContext1.start();

        result1Endpoint = consumerContext1.getEndpoint("mock:result1", MockEndpoint.class);
        result2Endpoint = consumerContext2.getEndpoint("mock:result2", MockEndpoint.class);
    }

    @AfterEach
    public void afterRun() {
        consumerContext1.stop();
        consumerContext2.stop();
        producerContext.stop();
        zkClientBean.destroy();
    }

    @Test
    public void testEndpoint() throws Exception {
        LOG.info("Starting consumerContext1");
        consumerContext1.start();
        assertMessageReceived(result1Endpoint, result2Endpoint);

        LOG.info("Starting consumerContext2");
        consumerContext2.start();
        assertMessageReceivedLoop(result1Endpoint, result2Endpoint, 3);

        LOG.info("Stopping consumerContext1");
        consumerContext1.stop();
        assertMessageReceivedLoop(result2Endpoint, result1Endpoint, 3);
    }

    protected void assertMessageReceivedLoop(MockEndpoint masterEndpoint, MockEndpoint standbyEndpoint, int count)
            throws Exception {
        for (int i = 0; i < count; i++) {
            Thread.sleep(1000);
            assertMessageReceived(masterEndpoint, standbyEndpoint);
        }
    }

    protected void assertMessageReceived(MockEndpoint masterEndpoint, MockEndpoint standbyEndpoint)
            throws InterruptedException {
        masterEndpoint.reset();
        standbyEndpoint.reset();

        String expectedBody = createNextExpectedBody();
        masterEndpoint.expectedBodiesReceived(expectedBody);
        standbyEndpoint.expectedMessageCount(0);

        template.sendBody("direct:start", expectedBody);

        LOG.info("Expecting master: {} and standby: {}", masterEndpoint, standbyEndpoint);
        MockEndpoint.assertIsSatisfied(masterEndpoint, standbyEndpoint);
    }

    protected String createNextExpectedBody() {
        return "body:" + messageCounter.incrementAndGet();
    }
}
