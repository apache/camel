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
package org.apache.camel.processor;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class WireTapStreamCachingTest extends ContextTestSupport {
    protected Endpoint startEndpoint;
    protected MockEndpoint x;
    protected MockEndpoint y;
    protected MockEndpoint z;

    public void testSendingAMessageUsingWiretapConvertsToReReadable() throws Exception {
        x.expectedBodiesReceived("<input/>+output");
        y.expectedBodiesReceived("<input/>+output");
        z.expectedBodiesReceived("<input/>+output");

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody(new StreamSource(new StringReader("<input/>")));
                in.setHeader("foo", "bar");
            }
        });

        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testSendingAMessageUsingWiretapShouldNotDeleteStreamFileBeforeAllExcangesAreComplete() throws InterruptedException {

        x.expectedMessageCount(1);
        y.expectedMessageCount(1);
        z.expectedMessageCount(1);

        // the used file should contain more than one character in order to be streamed into the file system
        template.sendBody("direct:a", this.getClass().getClassLoader().getResourceAsStream("org/apache/camel/processor/twoCharacters.txt"));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        x = getMockEndpoint("mock:x");
        y = getMockEndpoint("mock:y");
        z = getMockEndpoint("mock:z");
    }

    protected RouteBuilder createRouteBuilder() {
        final Processor processor = new Processor() {
            public void process(Exchange exchange) {
                // lets transform the IN message
                Message in = exchange.getIn();
                String body = in.getBody(String.class);
                in.setBody(body + "+output");
            }
        };

        return new RouteBuilder() {
            public void configure() {
                // enable stream caching
                context.setStreamCaching(true);
                // set stream threshold to 1, in order to stream into the file system
                context.getStreamCachingStrategy().setSpoolThreshold(1);

                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

                //stream caching should fix re-readability issues when wire tapping messages
                from("direct:a").wireTap("direct:x").wireTap("direct:y").wireTap("direct:z");

                from("direct:x").process(processor).to("mock:x");
                // even if a process takes more time then the others the wire tap shall work
                from("direct:y").delay(500).process(processor).to("mock:y");
                from("direct:z").process(processor).to("mock:z");
            }
        };
    }
}
