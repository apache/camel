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

import javax.xml.transform.stream.StreamSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class SplitterStreamCacheTest extends ContextTestSupport {

    private static final String TEST_FILE = "org/apache/camel/converter/stream/test.xml";
    protected int numMessages = 200;

    @Test
    public void testSendStreamSource() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(numMessages);

        for (int c = 0; c < numMessages; c++) {
            template.sendBody("seda:parallel", new StreamSource(getTestFileStream()));
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // ensure stream is spooled to disk
                context.getStreamCachingStrategy().setSpoolDirectory("target/tmp");
                context.getStreamCachingStrategy().setSpoolThreshold(-1);

                from("seda:parallel?concurrentConsumers=5").streamCaching().split(xpath("//person/city")).to("mock:result");
            }
        };
    }

    protected InputStream getTestFileStream() {
        InputStream answer = getClass().getClassLoader().getResourceAsStream(TEST_FILE);
        assertNotNull("Should have found the file: " + TEST_FILE + " on the classpath", answer);
        return answer;
    }
}
