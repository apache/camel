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

import org.apache.activemq.broker.BrokerService;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * test sending recieving from an MQTT broker
 */
public abstract class MQTTBaseTest extends CamelTestSupport {
    protected static final Logger LOG = LoggerFactory.getLogger(MQTTBaseTest.class);
    protected static final String TEST_TOPIC = "ComponentTestTopic";
    protected static final String TEST_TOPIC_2 = "AnotherTestTopic";
    protected static final String TEST_WILDCARD_TOPIC = "base/+/#";
    protected static final String TEST_TOPICS = TEST_TOPIC + "," + TEST_TOPIC_2;
    protected static final String TEST_TOPICS_WITH_WILDCARDS = TEST_TOPICS + "," + TEST_WILDCARD_TOPIC;
    protected BrokerService brokerService;
    protected int numberOfMessages = 10;


    public void setUp() throws Exception {
        brokerService = MQTTTestSupport.newBrokerService();
        brokerService.start();
        super.setUp();
    }


    public void tearDown() throws Exception {
        super.tearDown();
        if (brokerService != null) {
            brokerService.stop();
        }
    }
}
