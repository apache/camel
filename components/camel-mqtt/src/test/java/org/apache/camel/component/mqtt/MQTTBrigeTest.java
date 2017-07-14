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

import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Tests bridging between two mqtt topic using Camel
 * 
 * @version
 */
public class MQTTBrigeTest extends MQTTBaseTest {
    
    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @Produce(uri = "direct:startWorkaround")
    protected ProducerTemplate workaroundTemplate;

    @Test
    public void testMqttBridge() throws Exception {
        String expectedBody = "Dummy!";

        resultEndpoint.expectedBodiesReceived(expectedBody);

        template.sendBody(expectedBody);

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testMqttBridgeWorkAround() throws Exception {
        String expectedBody = "Dummy!";

        resultEndpoint.expectedBodiesReceived(expectedBody);

        workaroundTemplate.sendBody(expectedBody);

        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // Bridge message over two MQTT topics
                from("direct:start").to("mqtt:foo?publishTopicName=test/topic1&lazySessionCreation=false&host=" + MQTTTestSupport.getHostForMQTTEndpoint());

                from("mqtt:foo?subscribeTopicName=test/topic1&host=" + MQTTTestSupport.getHostForMQTTEndpoint()).to("log:testlogger?showAll=true")
                    .to("mqtt:foo?publishTopicName=test/resulttopic&lazySessionCreation=false&host=" + MQTTTestSupport.getHostForMQTTEndpoint())
                    .log(LoggingLevel.ERROR, "Message processed");

                // Bridge message over two MQTT topics with a seda in between
                from("direct:startWorkaround").to("mqtt:foo?publishTopicName=test/topic2&host=" + MQTTTestSupport.getHostForMQTTEndpoint());
                from("mqtt:foo?subscribeTopicName=test/topic2&host=" + MQTTTestSupport.getHostForMQTTEndpoint()).to("log:testlogger?showAll=true")
                    .to("seda:a");
                from("seda:a").to("mqtt:foo?publishTopicName=test/resulttopic&host=" + MQTTTestSupport.getHostForMQTTEndpoint())
                    .log(LoggingLevel.ERROR, "Message processed");
                // Forward the result to a mock endpoint to test
                from("mqtt:foo?subscribeTopicName=test/resulttopic&host=" + MQTTTestSupport.getHostForMQTTEndpoint()).to("mock:result");
            }
        };
    }
}
