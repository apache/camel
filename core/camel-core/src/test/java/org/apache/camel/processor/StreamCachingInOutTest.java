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
package org.apache.camel.processor;

import java.io.InputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class StreamCachingInOutTest extends ContextTestSupport {
    private static final String TEST_FILE = "org/apache/camel/processor/simple.txt";

    @Test
    public void testStreamCachingPerRoute() throws Exception {
        MockEndpoint c = getMockEndpoint("mock:c");
        c.expectedMessageCount(1);

        InputStream message = getTestFileStream();
        template.sendBody("direct:c", message);

        assertMockEndpointsSatisfied();
        assertEquals("James,Guillaume,Hiram,Rob,Roman", c.assertExchangeReceived(0).getIn().getBody(String.class));
    }

    @Test
    public void testStreamCachingPerRouteWithDirecVM() throws Exception {
        MockEndpoint e = getMockEndpoint("mock:e");
        e.expectedMessageCount(1);

        InputStream message = getTestFileStream();
        template.sendBody("direct:e", message);

        assertMockEndpointsSatisfied();
        assertEquals("James,Guillaume,Hiram,Rob,Roman", e.assertExchangeReceived(0).getIn().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getStreamCachingStrategy().setSpoolThreshold(1);
                from("direct:c").noStreamCaching().to("direct:d").convertBodyTo(String.class).to("mock:c");
                from("direct:d").streamCaching().process(new TestProcessor());
                from("direct:e").noStreamCaching().to("direct:f").convertBodyTo(String.class).to("mock:e");
                from("direct:f").streamCaching().process(new TestProcessor());
            }
        };
    }

    // have a test processor that reads the stream and makes sure it is reset
    private static class TestProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            InputStream is = exchange.getIn().getMandatoryBody(InputStream.class);
            while (is.available() > 0) {
                is.read();
            }
            is.close();
        }
    }

    // there is some special handling for ByteArrayInputStream so we read
    // InputStreams from a file
    private InputStream getTestFileStream() {
        InputStream answer = getClass().getClassLoader().getResourceAsStream(TEST_FILE);
        assertNotNull(answer, "Should have found the file: " + TEST_FILE + " on the classpath");
        return answer;
    }
}
