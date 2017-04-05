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
package org.apache.camel.component.zookeepermaster;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.util.ServiceHelper;
import org.apache.curator.framework.CuratorFramework;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MasterEndpointFailoverTest {
    private static final transient Logger LOG = LoggerFactory.getLogger(MasterEndpointFailoverTest.class);

    protected ProducerTemplate template;
    protected CamelContext producerContext;
    protected CamelContext consumerContext1;
    protected CamelContext consumerContext2;
    protected MockEndpoint result1Endpoint;
    protected MockEndpoint result2Endpoint;
    protected AtomicInteger messageCounter = new AtomicInteger(1);
    protected ZKServerFactoryBean serverFactoryBean = new ZKServerFactoryBean();
    protected CuratorFactoryBean zkClientBean = new CuratorFactoryBean();

    @Before
    public void beforeRun() throws Exception {
        System.out.println("Starting ZK server!");
        serverFactoryBean.setPort(9004);
        serverFactoryBean.afterPropertiesSet();

        // Create the zkClientBean
        zkClientBean.setConnectString("localhost:9004");
        CuratorFramework client = zkClientBean.getObject();

        // Need to bind the zookeeper client with the name "curator"
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("curator", client);

        producerContext = new DefaultCamelContext(registry);
        // Add the vm:start endpoint to avoid the NPE before starting the consumerContext1
        producerContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("vm:start");
            }
        });

        template = producerContext.createProducerTemplate();

        consumerContext1 = new DefaultCamelContext(registry);
        consumerContext1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("zookeeper-master:MasterEndpointFailoverTest:vm:start")
                    .to("log:result1")
                    .to("mock:result1");
            }
        });
        consumerContext2 = new DefaultCamelContext(registry);
        consumerContext2.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("zookeeper-master:MasterEndpointFailoverTest:vm:start")
                    .to("log:result2")
                    .to("mock:result2");
            }
        });
        // Need to start at less one consumerContext to enable the vm queue for producerContext
        ServiceHelper.startServices(consumerContext1);
        ServiceHelper.startServices(producerContext);
        result1Endpoint = consumerContext1.getEndpoint("mock:result1", MockEndpoint.class);
        result2Endpoint = consumerContext2.getEndpoint("mock:result2", MockEndpoint.class);
    }

    @After
    public void afterRun() throws Exception {
        ServiceHelper.stopServices(consumerContext1);
        ServiceHelper.stopServices(consumerContext2);
        ServiceHelper.stopServices(producerContext);
        zkClientBean.destroy();
        serverFactoryBean.destroy();
    }

    @Test
    public void testEndpoint() throws Exception {
        System.out.println("Starting consumerContext1");

        ServiceHelper.startServices(consumerContext1);
        assertMessageReceived(result1Endpoint, result2Endpoint);

        System.out.println("Starting consumerContext2");
        ServiceHelper.startServices(consumerContext2);
        assertMessageReceivedLoop(result1Endpoint, result2Endpoint, 3);

        System.out.println("Stopping consumerContext1");
        ServiceHelper.stopService(consumerContext1);
        assertMessageReceivedLoop(result2Endpoint, result1Endpoint, 3);
    }

    protected void assertMessageReceivedLoop(MockEndpoint masterEndpoint, MockEndpoint standbyEndpoint, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            Thread.sleep(1000);
            assertMessageReceived(masterEndpoint, standbyEndpoint);
        }
    }

    protected void assertMessageReceived(MockEndpoint masterEndpoint, MockEndpoint standbyEndpoint) throws InterruptedException {
        masterEndpoint.reset();
        standbyEndpoint.reset();

        String expectedBody = createNextExpectedBody();
        masterEndpoint.expectedBodiesReceived(expectedBody);
        standbyEndpoint.expectedMessageCount(0);

        template.sendBody("direct:start", expectedBody);

        LOG.info("Expecting master: " + masterEndpoint + " and standby: " + standbyEndpoint);
        MockEndpoint.assertIsSatisfied(masterEndpoint, standbyEndpoint);
    }

    protected String createNextExpectedBody() {
        return "body:" + messageCounter.incrementAndGet();
    }
}
