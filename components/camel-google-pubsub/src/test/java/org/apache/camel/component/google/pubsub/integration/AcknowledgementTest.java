/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.google.pubsub.integration;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.PubsubTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.junit.BeforeClass;
import org.junit.Test;

public class AcknowledgementTest extends PubsubTestSupport {

    private static final String topicName="failureSingle";
    private static final String subscriptionName="failureSub";

    @Produce(uri = "direct:in")
    protected ProducerTemplate producer;

    @EndpointInject(uri = "direct:in")
    private Endpoint directIn;

    @EndpointInject(uri = "google-pubsub:{{project.id}}:"+topicName)
    private Endpoint pubsubTopic;

    @EndpointInject(uri = "google-pubsub:{{project.id}}:"+subscriptionName)
    private Endpoint pubsubSubscription;

    @EndpointInject(uri = "mock:receiveResult")
    protected MockEndpoint receiveResult;

    @BeforeClass
    public static void createTopicSubscription() throws Exception{
        createTopicSubscriptionPair(topicName, subscriptionName);
    }

    public static Boolean FAIL = false;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                 from(directIn)
                        .routeId("Send_to_Fail")
                        .to(pubsubTopic);

                from(pubsubSubscription)
                        .routeId("Fail_Receive")
                        .autoStartup(true)
                        .process(
                                new Processor() {
                                    @Override
                                    public void process(Exchange exchange) throws Exception {
                                        if (AcknowledgementTest.FAIL){
                                            Thread.sleep(750);
                                            throw new Exception("FAIL");
                                        }
                                    }
                                }
                        )
                        .to(receiveResult);
            }
        };
    }

    /**
     * Testing acknowledgements.
     * Three checks to be performed.
     *
     * Check 1 : Successful round trip.
     * Message received and acknowledged.
     * If the ACK fails for the first message, it will be delivered again for the second check and the body comparison will fail.
     *
     * Check 2 : Failure. As the route throws and exception and the message is NACK'ed.
     * The message should remain in the PubSub Subscription for the third check.
     *
     * Check 3 : Success for the second message.
     * The message received should match the second message sent.
     *
     * @throws Exception
     */

    @Test
    public void singleMessage() throws Exception {

        Exchange firstExchange = new DefaultExchange(context);
        Exchange secondExchange = new DefaultExchange(context);

        firstExchange.getIn().setBody("SUCCESS  : " + firstExchange.getExchangeId());
        secondExchange.getIn().setBody("FAIL  : " + secondExchange.getExchangeId());

        // Check 1 : Successful roundtrip.
        System.out.println("Acknowledgement Test : Stage 1");
        receiveResult.reset();
        FAIL = false;
        receiveResult.expectedMessageCount( 1);
        receiveResult.expectedBodiesReceivedInAnyOrder(firstExchange.getIn().getBody());
        producer.send(firstExchange);
        receiveResult.assertIsSatisfied(3000);

        // Check 2 : Failure for the second message.
        System.out.println("Acknowledgement Test : Stage 2");
        receiveResult.reset();
        FAIL = true;
        receiveResult.expectedMessageCount(0);
        producer.send(secondExchange);
        receiveResult.assertIsSatisfied(3000);

        // Check 3 : Success for the second message.
        System.out.println("Acknowledgement Test : Stage 3");
        receiveResult.reset();
        FAIL = false;
        receiveResult.expectedMessageCount(1);
        receiveResult.expectedBodiesReceivedInAnyOrder(secondExchange.getIn().getBody());
        receiveResult.assertIsSatisfied(3000);
    }
}
