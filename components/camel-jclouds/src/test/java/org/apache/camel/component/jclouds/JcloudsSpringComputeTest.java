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
package org.apache.camel.component.jclouds;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelSpringTestSupport;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class JcloudsSpringComputeTest extends CamelSpringTestSupport {

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint result;

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("classpath:compute-test.xml");
    }

    @Test
    public void testListImages() throws InterruptedException {
        template.sendBodyAndHeader("direct:start", "Some message", JcloudsConstants.OPERATION, JcloudsConstants.LIST_IMAGES);
        result.expectedMessageCount(1);
        result.assertIsSatisfied();
        List<Exchange> exchanges = result.getExchanges();
        if (exchanges != null && !exchanges.isEmpty()) {
            for (Exchange exchange : exchanges) {
                Set images = (Set) exchange.getIn().getBody();
                assertTrue(images.size() > 0);
                for (Object obj : images) {
                    assertTrue(obj instanceof Image);
                }
            }
        }
    }

    @Test
    public void testListHardware() throws InterruptedException {
        template.sendBodyAndHeader("direct:start", "Some message", JcloudsConstants.OPERATION, JcloudsConstants.LIST_HARDWARE);
        result.expectedMessageCount(1);
        result.assertIsSatisfied();
        List<Exchange> exchanges = result.getExchanges();
        if (exchanges != null && !exchanges.isEmpty()) {
            for (Exchange exchange : exchanges) {
                Set hardwares = (Set) exchange.getIn().getBody();
                assertTrue(hardwares.size() > 0);
                for (Object obj : hardwares) {
                    assertTrue(obj instanceof Hardware);
                }
            }
        }
    }

    @Test
    public void testListNodes() throws InterruptedException {
        template.sendBodyAndHeader("direct:start", "Some message", JcloudsConstants.OPERATION, JcloudsConstants.LIST_NODES);
        result.expectedMessageCount(1);
        result.assertIsSatisfied();
        List<Exchange> exchanges = result.getExchanges();
        if (exchanges != null && !exchanges.isEmpty()) {
            for (Exchange exchange : exchanges) {
                Set nodeMetadatas = (Set) exchange.getIn().getBody();
                assertEquals("Nodes should be 0", 0, nodeMetadatas.size());
            }
        }
    }


    @Test
    public void testCreateAndDestroyNode() throws InterruptedException {
        Map<String, Object> createHeaders = new HashMap<String, Object>();
        createHeaders.put(JcloudsConstants.OPERATION, JcloudsConstants.CREATE_NODE);
        createHeaders.put(JcloudsConstants.IMAGE_ID, "1");
        createHeaders.put(JcloudsConstants.GROUP, "default");

        Map<String, Object> destroyHeaders = new HashMap<String, Object>();
        destroyHeaders.put(JcloudsConstants.OPERATION, JcloudsConstants.DESTROY_NODE);

        template.sendBodyAndHeaders("direct:start", "Some message", createHeaders);
        result.expectedMessageCount(1);
        result.assertIsSatisfied();
        List<Exchange> exchanges = result.getExchanges();
        if (exchanges != null && !exchanges.isEmpty()) {
            for (Exchange exchange : exchanges) {
                Set nodeMetadatas = (Set) exchange.getIn().getBody();
                assertEquals("There should be no node running", 1, nodeMetadatas.size());

                for (Object obj : nodeMetadatas) {
                    NodeMetadata nodeMetadata = (NodeMetadata) obj;
                    destroyHeaders.put(JcloudsConstants.NODE_ID, nodeMetadata.getId());
                    template.sendBodyAndHeaders("direct:start", "Some message", destroyHeaders);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Ignore("For now not possible to combine stub provider with ssh module, requird for runScript")
    @Test
    public void testRunScript() throws InterruptedException {
        Map<String, Object> createHeaders = new HashMap<String, Object>();
        createHeaders.put(JcloudsConstants.OPERATION, JcloudsConstants.CREATE_NODE);
        createHeaders.put(JcloudsConstants.IMAGE_ID, "1");
        createHeaders.put(JcloudsConstants.GROUP, "default");

        Map<String, Object> runScriptHeaders = new HashMap<String, Object>();
        runScriptHeaders.put(JcloudsConstants.OPERATION, JcloudsConstants.RUN_SCRIPT);

        Map<String, Object> destroyHeaders = new HashMap<String, Object>();
        destroyHeaders.put(JcloudsConstants.OPERATION, JcloudsConstants.DESTROY_NODE);

        Set<? extends NodeMetadata> nodeMetadatas = (Set<? extends NodeMetadata>) template.requestBodyAndHeaders("direct:in-out", "Some message", createHeaders);
        assertEquals("There should be a node running", 1, nodeMetadatas.size());
        for (NodeMetadata nodeMetadata : nodeMetadatas) {
            runScriptHeaders.put(JcloudsConstants.NODE_ID, nodeMetadata.getId());
            destroyHeaders.put(JcloudsConstants.NODE_ID, nodeMetadata.getId());
            template.requestBodyAndHeaders("direct:in-out", "Some message", runScriptHeaders);
            template.sendBodyAndHeaders("direct:in-out", "Some message", destroyHeaders);
        }
    }
}
