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
package org.apache.camel.component.mqtt;

import org.apache.camel.Endpoint;
import org.fusesource.mqtt.client.QoS;
import org.junit.Test;

public class MQTTConfigurationTest extends MQTTBaseTest {

    @Test
    public void testBasicConfiguration() throws Exception {
        Endpoint endpoint = context.getEndpoint("mqtt:todo?byDefaultRetain=true&qualityOfService=exactlyOnce&publishTopicName=" + TEST_TOPIC + "&subscribeTopicName=" + TEST_TOPIC
                + "&host=" + MQTTTestSupport.getHostForMQTTEndpoint());
        assertTrue("Endpoint not a MQTTEndpoint: " + endpoint, endpoint instanceof MQTTEndpoint);
        MQTTEndpoint mqttEndpoint = (MQTTEndpoint) endpoint;

        assertEquals(mqttEndpoint.getConfiguration().getQoS(), QoS.EXACTLY_ONCE);
        assertEquals(mqttEndpoint.getConfiguration().getPublishTopicName(), TEST_TOPIC);
        assertEquals(mqttEndpoint.getConfiguration().getSubscribeTopicName(), TEST_TOPIC);
        assertTrue(mqttEndpoint.getConfiguration().isByDefaultRetain());
    }

    @Test
    public void testMultipleSubscribeTopicsConfiguration() throws Exception {
        Endpoint endpoint = context.getEndpoint("mqtt:todo?byDefaultRetain=true&qualityOfService=exactlyOnce&publishTopicName=" + TEST_TOPIC + "&subscribeTopicNames=" + TEST_TOPICS
                + "&host=" + MQTTTestSupport.getHostForMQTTEndpoint());
        assertTrue("Endpoint not a MQTTEndpoint: " + endpoint, endpoint instanceof MQTTEndpoint);
        MQTTEndpoint mqttEndpoint = (MQTTEndpoint) endpoint;

        assertEquals(mqttEndpoint.getConfiguration().getQoS(), QoS.EXACTLY_ONCE);
        assertEquals(mqttEndpoint.getConfiguration().getPublishTopicName(), TEST_TOPIC);
        assertEquals(mqttEndpoint.getConfiguration().getSubscribeTopicNames(), TEST_TOPICS);
        assertTrue(mqttEndpoint.getConfiguration().isByDefaultRetain());
    }

    @Test
    public void testWildcardSubscribeTopicsConfiguration() throws Exception {
        Endpoint endpoint = context.getEndpoint("mqtt:todo?byDefaultRetain=true&qualityOfService=exactlyOnce&publishTopicName=" + TEST_TOPIC + "&subscribeTopicNames=" + TEST_TOPICS_WITH_WILDCARDS
                + "&host=" + MQTTTestSupport.getHostForMQTTEndpoint());
        assertTrue("Endpoint not a MQTTEndpoint: " + endpoint, endpoint instanceof MQTTEndpoint);
        MQTTEndpoint mqttEndpoint = (MQTTEndpoint) endpoint;

        assertEquals(mqttEndpoint.getConfiguration().getQoS(), QoS.EXACTLY_ONCE);
        assertEquals(mqttEndpoint.getConfiguration().getPublishTopicName(), TEST_TOPIC);
        assertEquals(mqttEndpoint.getConfiguration().getSubscribeTopicNames(), TEST_TOPICS_WITH_WILDCARDS);
        assertTrue(mqttEndpoint.getConfiguration().isByDefaultRetain());
    }
}
