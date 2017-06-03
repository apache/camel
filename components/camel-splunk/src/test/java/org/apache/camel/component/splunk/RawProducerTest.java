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
package org.apache.camel.component.splunk;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class RawProducerTest extends SplunkMockTestSupport {
    private static final String PAYLOAD = "{foo:1, bar:2}";

    @EndpointInject(uri = "splunk://stream")
    protected SplunkEndpoint streamEndpoint;

    @EndpointInject(uri = "splunk://submit")
    protected SplunkEndpoint submitEndpoint;

    @EndpointInject(uri = "splunk://tcp")
    protected SplunkEndpoint tcpEndpoint;

    @Test
    public void testStreamWriter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:stream-result");
        mock.setExpectedMessageCount(1);
        mock.expectedBodiesReceived(PAYLOAD);
        template.sendBody("direct:stream", PAYLOAD);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSubmitWriter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:submitresult");
        mock.setExpectedMessageCount(1);
        mock.expectedBodiesReceived(PAYLOAD);
        template.sendBody("direct:submit", PAYLOAD);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTcpWriter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tcpresult");
        mock.setExpectedMessageCount(1);
        mock.expectedBodiesReceived(PAYLOAD);
        template.sendBody("direct:tcp", PAYLOAD);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:stream").to("splunk://stream?username=foo&password=bar&index=myindex&sourceType=SourceType&source=Source&raw=true").to("mock:stream-result");

                from("direct:submit").to("splunk://submit?username=foo&password=bar&index=myindex&sourceType=testSource&source=test&raw=true").to("mock:submitresult");

                from("direct:tcp").to("splunk://tcp?username=foo&password=bar&tcpReceiverPort=2222&index=myindex&sourceType=testSource&source=test&raw=true").to("mock:tcpresult");
            }
        };
    }

}
