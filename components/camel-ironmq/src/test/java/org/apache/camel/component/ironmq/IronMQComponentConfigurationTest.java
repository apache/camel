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
package org.apache.camel.component.ironmq;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class IronMQComponentConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        IronMQComponent component = new IronMQComponent(context);
        IronMQEndpoint endpoint = (IronMQEndpoint)component.createEndpoint("ironmq://TestQueue?projectId=xxx&token=yyy");

        assertEquals("TestQueue", endpoint.getConfiguration().getQueueName());
        assertEquals("xxx", endpoint.getConfiguration().getProjectId());
        assertEquals("yyy", endpoint.getConfiguration().getToken());
        assertEquals(0, endpoint.getConfiguration().getVisibilityDelay());
        assertEquals(1, endpoint.getConfiguration().getMaxMessagesPerPoll());
        assertEquals(60, endpoint.getConfiguration().getTimeout());
        assertFalse(endpoint.getConfiguration().isPreserveHeaders());
        assertFalse(endpoint.getConfiguration().isBatchDelete());
        assertEquals(0, endpoint.getConfiguration().getWait());
    }

    @Test
    public void createEndpointWithMinimalConfigurationAndIronMQCloud() throws Exception {
        IronMQComponent component = new IronMQComponent(context);

        IronMQEndpoint endpoint = (IronMQEndpoint)component.createEndpoint("ironmq://TestQueue?projectId=xxx&token=yyy&ironMQCloud=https://iron.foo");

        assertEquals("TestQueue", endpoint.getConfiguration().getQueueName());
        assertEquals("xxx", endpoint.getConfiguration().getProjectId());
        assertEquals("yyy", endpoint.getConfiguration().getToken());
        assertEquals(0, endpoint.getConfiguration().getVisibilityDelay());
        assertEquals(1, endpoint.getConfiguration().getMaxMessagesPerPoll());
        assertEquals(60, endpoint.getConfiguration().getTimeout());
        assertEquals("https://iron.foo", endpoint.getConfiguration().getIronMQCloud());
    }

    @Test
    public void createEndpointWithMaximalConfiguration() throws Exception {
        IronMQComponent component = new IronMQComponent(context);
        IronMQEndpoint endpoint = (IronMQEndpoint)component
            .createEndpoint("ironmq://TestQueue?projectId=xxx&token=yyy&timeout=120&visibilityDelay=5&maxMessagesPerPoll=20&preserveHeaders=true&wait=30"
                            + "&ironMQCloud=https://iron.foo&batchDelete=true");
        assertEquals("TestQueue", endpoint.getConfiguration().getQueueName());
        assertEquals("xxx", endpoint.getConfiguration().getProjectId());
        assertEquals("yyy", endpoint.getConfiguration().getToken());
        assertEquals(20, endpoint.getConfiguration().getMaxMessagesPerPoll());
        assertEquals(120, endpoint.getConfiguration().getTimeout());
        assertEquals(5, endpoint.getConfiguration().getVisibilityDelay());
        assertTrue(endpoint.getConfiguration().isPreserveHeaders());
        assertEquals(30, endpoint.getConfiguration().getWait());
        assertTrue(endpoint.getConfiguration().isBatchDelete());
        assertEquals("https://iron.foo", endpoint.getConfiguration().getIronMQCloud());
    }

    @Test
    public void createEndpointWithPollConsumerConfiguration() throws Exception {
        IronMQComponent component = new IronMQComponent(context);
        IronMQEndpoint endpoint = (IronMQEndpoint)component
            .createEndpoint("ironmq://TestQueue?projectId=xxx&token=yyy&initialDelay=200&delay=400&timeout=120&maxMessagesPerPoll=20");
        IronMQConsumer consumer = (IronMQConsumer)endpoint.createConsumer(null);

        assertEquals(200, consumer.getInitialDelay());
        assertEquals(400, consumer.getDelay());
        assertEquals(20, consumer.getMaxMessagesPerPoll());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutTokenConfiguration() throws Exception {
        IronMQComponent component = new IronMQComponent(context);
        component.createEndpoint("ironmq://testqueue?projectId=yyy");
    }

    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutProjectIdConfiguration() throws Exception {
        IronMQComponent component = new IronMQComponent(context);
        component.createEndpoint("ironmq://MyQueue?token=xxx");
    }
}
