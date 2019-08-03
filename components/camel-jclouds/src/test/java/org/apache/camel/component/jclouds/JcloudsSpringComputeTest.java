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
package org.apache.camel.component.jclouds;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class JcloudsSpringComputeTest extends CamelSpringTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint result;
    
    @EndpointInject("mock:resultlist")
    protected MockEndpoint resultlist;

    @Override
    @After
    public void tearDown() throws Exception {
        template.sendBodyAndHeaders("direct:start", null, destroyHeaders(null, null));
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("classpath:compute-test.xml");
    }

    @Test
    public void testListImages() throws InterruptedException {
        result.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:start", null, JcloudsConstants.OPERATION, JcloudsConstants.LIST_IMAGES);
        result.assertIsSatisfied();

        List<Exchange> exchanges = result.getExchanges();
        if (exchanges != null && !exchanges.isEmpty()) {
            for (Exchange exchange : exchanges) {
                Set<?> images = exchange.getIn().getBody(Set.class);
                assertTrue(images.size() > 0);
                for (Object obj : images) {
                    assertTrue(obj instanceof Image);
                }
            }
        }
    }

    @Test
    public void testListHardware() throws InterruptedException {
        result.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:start", null, JcloudsConstants.OPERATION, JcloudsConstants.LIST_HARDWARE);
        result.assertIsSatisfied();

        List<Exchange> exchanges = result.getExchanges();
        if (exchanges != null && !exchanges.isEmpty()) {
            for (Exchange exchange : exchanges) {
                Set<?> hardwares = exchange.getIn().getBody(Set.class);
                assertTrue(hardwares.size() > 0);
                for (Object obj : hardwares) {
                    assertTrue(obj instanceof Hardware);
                }
            }
        }
    }

    @Test
    public void testListNodes() throws InterruptedException {
        result.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:start", null, JcloudsConstants.OPERATION, JcloudsConstants.LIST_NODES);
        result.assertIsSatisfied();

        List<Exchange> exchanges = result.getExchanges();
        if (exchanges != null && !exchanges.isEmpty()) {
            for (Exchange exchange : exchanges) {
                Set<?> nodeMetadatas = exchange.getIn().getBody(Set.class);
                assertEquals("Nodes should be 0", 0, nodeMetadatas.size());
            }
        }
    }

    @Test
    public void testCreateAndListNodes() throws InterruptedException {
        result.expectedMessageCount(2);
        template.sendBodyAndHeaders("direct:start", null, createHeaders("1", "default"));
        template.sendBodyAndHeader("direct:start", null, JcloudsConstants.OPERATION, JcloudsConstants.LIST_NODES);
        result.assertIsSatisfied();

        List<Exchange> exchanges = result.getExchanges();
        if (exchanges != null && !exchanges.isEmpty()) {
            for (Exchange exchange : exchanges) {
                Set<?> nodeMetadatas = exchange.getIn().getBody(Set.class);
                assertEquals("Nodes should be 1", 1, nodeMetadatas.size());
            }
        }
    }


    @Test
    public void testCreateAndListWithPredicates() throws InterruptedException {
        result.expectedMessageCount(6);

        //Create a node for the default group
        template.sendBodyAndHeaders("direct:start", null, createHeaders("1", "default"));

        //Create a node for the group 'other'
        template.sendBodyAndHeaders("direct:start", null, createHeaders("1", "other"));
        template.sendBodyAndHeaders("direct:start", null, createHeaders("2", "other"));

        template.sendBodyAndHeaders("direct:start", null, listNodeHeaders(null, "other", null));
        template.sendBodyAndHeaders("direct:start", null, listNodeHeaders("3", "other", null));
        template.sendBodyAndHeaders("direct:start", null, listNodeHeaders("3", "other", "RUNNING"));

        result.assertIsSatisfied();
    }

    @Test
    public void testCreateAndDestroyNode() throws InterruptedException {
        result.expectedMessageCount(1);
        template.sendBodyAndHeaders("direct:start", null, createHeaders("1", "default"));
        result.assertIsSatisfied();

        List<Exchange> exchanges = result.getExchanges();
        if (exchanges != null && !exchanges.isEmpty()) {
            for (Exchange exchange : exchanges) {
                Set<?> nodeMetadatas = exchange.getIn().getBody(Set.class);
                assertEquals("There should be no node running", 1, nodeMetadatas.size());

                for (Object obj : nodeMetadatas) {
                    NodeMetadata nodeMetadata = (NodeMetadata) obj;
                    template.sendBodyAndHeaders("direct:start", null, destroyHeaders(nodeMetadata.getId(), null));
                }
            }
        }
    }
    
    @Test
    public void testCreateAndRebootNode() throws InterruptedException {
        result.expectedMessageCount(1);
        template.sendBodyAndHeaders("direct:start", null, createHeaders("1", "default"));
        result.assertIsSatisfied();

        List<Exchange> exchanges = result.getExchanges();
        if (exchanges != null && !exchanges.isEmpty()) {
            for (Exchange exchange : exchanges) {
                Set<?> nodeMetadatas = exchange.getIn().getBody(Set.class);
                assertEquals("There should be one node running", 1, nodeMetadatas.size());

                for (Object obj : nodeMetadatas) {
                    NodeMetadata nodeMetadata = (NodeMetadata) obj;
                    template.sendBodyAndHeaders("direct:start", null, rebootHeaders(nodeMetadata.getId(), null));
                }
            }
        }
    }
    
    @Test
    public void testCreateAndSuspendNode() throws InterruptedException {
        result.expectedMessageCount(1);
        template.sendBodyAndHeaders("direct:start", null, createHeaders("1", "default"));
        result.assertIsSatisfied();

        List<Exchange> exchanges = result.getExchanges();
        if (exchanges != null && !exchanges.isEmpty()) {
            for (Exchange exchange : exchanges) {
                Set<?> nodeMetadatas = exchange.getIn().getBody(Set.class);
                assertEquals("There should be one node running", 1, nodeMetadatas.size());

                for (Object obj : nodeMetadatas) {
                    NodeMetadata nodeMetadata = (NodeMetadata) obj;
                    template.sendBodyAndHeaders("direct:start", null, suspendHeaders(nodeMetadata.getId(), null));
                }
            }
        }
    }
    
    @Test
    public void testCreateSuspendAndResumeNode() throws InterruptedException {
        result.expectedMessageCount(1);
        template.sendBodyAndHeaders("direct:start", null, createHeaders("1", "default"));
        result.assertIsSatisfied();

        List<Exchange> exchanges = result.getExchanges();
        if (exchanges != null && !exchanges.isEmpty()) {
            for (Exchange exchange : exchanges) {
                Set<?> nodeMetadatas = exchange.getIn().getBody(Set.class);
                assertEquals("There should be one node running", 1, nodeMetadatas.size());

                for (Object obj : nodeMetadatas) {
                    NodeMetadata nodeMetadata = (NodeMetadata) obj;
                    template.sendBodyAndHeaders("direct:start", null, resumeHeaders(nodeMetadata.getId(), null));
                }
            }
        }
    }   

    @SuppressWarnings("unchecked")
    @Ignore("For now not possible to combine stub provider with ssh module, required for runScript")
    @Test
    public void testRunScript() throws InterruptedException {
        Map<String, Object> runScriptHeaders = new HashMap<>();
        runScriptHeaders.put(JcloudsConstants.OPERATION, JcloudsConstants.RUN_SCRIPT);

        Set<? extends NodeMetadata> nodeMetadatas = (Set<? extends NodeMetadata>) template.requestBodyAndHeaders("direct:in-out", null, createHeaders("1", "default"));
        assertEquals("There should be a node running", 1, nodeMetadatas.size());
        for (NodeMetadata nodeMetadata : nodeMetadatas) {
            runScriptHeaders.put(JcloudsConstants.NODE_ID, nodeMetadata.getId());
            template.requestBodyAndHeaders("direct:in-out", null, runScriptHeaders);
            template.sendBodyAndHeaders("direct:in-out", null, destroyHeaders(nodeMetadata.getId(), null));
        }
    }


    /**
     * Returns a {@Map} with the create headers.
     *
     * @param imageId The imageId to use for creating the node.
     * @param group   The group to be assigned to the node.
     */
    protected Map<String, Object> createHeaders(String imageId, String group) {
        Map<String, Object> createHeaders = new HashMap<>();
        createHeaders.put(JcloudsConstants.OPERATION, JcloudsConstants.CREATE_NODE);
        createHeaders.put(JcloudsConstants.IMAGE_ID, imageId);
        createHeaders.put(JcloudsConstants.GROUP, group);
        return createHeaders;
    }


    /**
     * Returns a {@Map} with the destroy headers.
     *
     * @param nodeId The id of the node to destroy.
     * @param group  The group of the node to destroy.
     */
    protected Map<String, Object> destroyHeaders(String nodeId, String group) {
        Map<String, Object> destroyHeaders = new HashMap<>();
        destroyHeaders.put(JcloudsConstants.OPERATION, JcloudsConstants.DESTROY_NODE);
        if (nodeId != null) {
            destroyHeaders.put(JcloudsConstants.NODE_ID, nodeId);
        }
        if (group != null) {
            destroyHeaders.put(JcloudsConstants.GROUP, group);
        }
        return destroyHeaders;
    }

    /**
     * Returns a {@Map} with the destroy headers.
     *
     * @param nodeId The id of the node to destroy.
     * @param group  The group of the node to destroy.
     */
    protected Map<String, Object> listNodeHeaders(String nodeId, String group, Object state) {
        Map<String, Object> listHeaders = new HashMap<>();
        listHeaders.put(JcloudsConstants.OPERATION, JcloudsConstants.LIST_NODES);
        if (nodeId != null) {
            listHeaders.put(JcloudsConstants.NODE_ID, nodeId);
        }

        if (group != null) {
            listHeaders.put(JcloudsConstants.GROUP, group);
        }

        if (state != null) {
            listHeaders.put(JcloudsConstants.NODE_STATE, state);
        }

        return listHeaders;
    }
    
    /**
     * Returns a {@Map} with the reboot headers.
     *
     * @param nodeId The id of the node to reboot.
     * @param group  The group of the node to reboot.
     */
    protected Map<String, Object> rebootHeaders(String nodeId, String group) {
        Map<String, Object> rebootHeaders = new HashMap<>();
        rebootHeaders.put(JcloudsConstants.OPERATION, JcloudsConstants.REBOOT_NODE);
        if (nodeId != null) {
            rebootHeaders.put(JcloudsConstants.NODE_ID, nodeId);
        }
        if (group != null) {
            rebootHeaders.put(JcloudsConstants.GROUP, group);
        }
        return rebootHeaders;
    }
    
    /**
     * Returns a {@Map} with the suspend headers.
     *
     * @param nodeId The id of the node to suspend.
     * @param group  The group of the node to suspend.
     */
    protected Map<String, Object> suspendHeaders(String nodeId, String group) {
        Map<String, Object> rebootHeaders = new HashMap<>();
        rebootHeaders.put(JcloudsConstants.OPERATION, JcloudsConstants.SUSPEND_NODE);
        if (nodeId != null) {
            rebootHeaders.put(JcloudsConstants.NODE_ID, nodeId);
        }
        if (group != null) {
            rebootHeaders.put(JcloudsConstants.GROUP, group);
        }
        return rebootHeaders;
    }
    
    /**
     * Returns a {@Map} with the suspend headers.
     *
     * @param nodeId The id of the node to resume.
     * @param group  The group of the node to resume.
     */
    protected Map<String, Object> resumeHeaders(String nodeId, String group) {
        Map<String, Object> rebootHeaders = new HashMap<>();
        rebootHeaders.put(JcloudsConstants.OPERATION, JcloudsConstants.RESUME_NODE);
        if (nodeId != null) {
            rebootHeaders.put(JcloudsConstants.NODE_ID, nodeId);
        }
        if (group != null) {
            rebootHeaders.put(JcloudsConstants.GROUP, group);
        }
        return rebootHeaders;
    }
}
