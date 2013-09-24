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
package org.apache.camel.component.splunk.integration;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.splunk.event.SplunkEvent;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("run manually since it requires a running local splunk server")
public class SplunkProducerTest extends SplunkTest {

    // Splunk tcp reciever port configured in Splunk
    private static final String TCP_RECIEVER_PORT = "9997";

    @Test
    public void testStreamWriter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:stream-result");
        mock.expectedMinimumMessageCount(1);
        SplunkEvent splunkEvent = new SplunkEvent();
        splunkEvent.addPair("key11", "value1");
        splunkEvent.addPair("key22", "value2");
        splunkEvent.addPair("key33", "value3");
        template.sendBody("direct:stream", splunkEvent);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSubmitWriter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:submitresult");
        mock.expectedMinimumMessageCount(1);
        SplunkEvent splunkEvent = new SplunkEvent();
        splunkEvent.addPair("key1", "value1");
        splunkEvent.addPair("key2", "value2");
        splunkEvent.addPair("key3", "value1");
        template.sendBody("direct:submit", splunkEvent);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTcpWriter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tcpresult");
        mock.expectedMinimumMessageCount(1);
        SplunkEvent splunkEvent = new SplunkEvent();
        splunkEvent.addPair("key1", "value1");
        splunkEvent.addPair("key2", "value2");
        splunkEvent.addPair("key3", "value3");
        template.sendBody("direct:tcp", splunkEvent);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:stream").to("splunk://stream?username=" + SPLUNK_USERNAME + "&password=" + SPLUNK_PASSWORD + "&index=" + INDEX
                                             + "&sourceType=StreamSourceType&source=StreamSource").to("mock:stream-result");

                from("direct:submit").to("splunk://submit?username=" + SPLUNK_USERNAME + "&password=" + SPLUNK_PASSWORD + "&index=" + INDEX + "&sourceType=testSource&source=test")
                    .to("mock:submitresult");

                from("direct:tcp").to("splunk://tcp?username=" + SPLUNK_USERNAME + "&password=" + SPLUNK_PASSWORD + "&tcpReceiverPort=" + TCP_RECIEVER_PORT + "&index=" + INDEX
                                          + "&sourceType=testSource&source=test").to("mock:tcpresult");
            }
        };
    }
}
