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
package org.apache.camel.component.sjms.jms;

import javax.jms.Queue;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;

import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.Test;

public class DefaultDestinationCreationStrategyTest extends JmsTestSupport {

    private DestinationCreationStrategy strategy = new DefaultDestinationCreationStrategy();

    @Test
    public void testQueueCreation() throws Exception {
        Queue destination = (Queue)strategy.createDestination(getSession(), "queue://test", false);
        assertNotNull(destination);
        assertEquals("test", destination.getQueueName());

        destination = (Queue)strategy.createDestination(getSession(), "test", false);
        assertNotNull(destination);
        assertEquals("test", destination.getQueueName());
    }

    @Test
    public void testTopicCreation() throws Exception {
        Topic destination = (Topic)strategy.createDestination(getSession(), "topic://test", true);
        assertNotNull(destination);
        assertEquals("test", destination.getTopicName());

        destination = (Topic)strategy.createDestination(getSession(), "test", true);
        assertNotNull(destination);
        assertEquals("test", destination.getTopicName());
    }

    @Test
    public void testTemporaryQueueCreation() throws Exception {
        TemporaryQueue destination = (TemporaryQueue)strategy.createTemporaryDestination(getSession(), false);
        assertNotNull(destination);
        assertNotNull(destination.getQueueName());
    }

    @Test
    public void testTemporaryTopicCreation() throws Exception {
        TemporaryTopic destination = (TemporaryTopic)strategy.createTemporaryDestination(getSession(), true);
        assertNotNull(destination);
        assertNotNull(destination.getTopicName());
    }
}