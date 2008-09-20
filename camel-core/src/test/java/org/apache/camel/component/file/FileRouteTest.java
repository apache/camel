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
package org.apache.camel.component.file;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class FileRouteTest extends ContextTestSupport {
    protected Object expectedBody = "Hello there!";
    protected String targetdir = "target/test-default-inbox";
    protected String params = "?consumer.recursive=true";
    protected String uri = "file:" + targetdir + params;
    protected LockRecorderProcessor recorder = new LockRecorderProcessor();

    public void testFileRoute() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived(expectedBody);
        result.setResultWaitTime(5000);

        template.sendBodyAndHeader(uri, expectedBody, "cheese", 123);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        deleteDirectory(targetdir);
        uri = "file:" + targetdir + params;
        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(uri).process(recorder).to("mock:result");
            }
        };
    }

    public class LockRecorderProcessor implements Processor {
        private ConcurrentLinkedQueue<String> locks = new ConcurrentLinkedQueue<String>();

        public ConcurrentLinkedQueue<String> getLocks() {
            return locks;
        }

        public void process(Exchange exchange) {
            locks.add(exchange.getProperty("org.apache.camel.file.lock.name", String.class));
        }
    }
}
