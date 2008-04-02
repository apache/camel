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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class FileRouteGenerateEmptyExchangeWhenIdleTest extends ContextTestSupport {
    protected Object expectedBody = "Hello there!";
    protected String uri = "file:target/test-inbox2?consumer.generateEmptyExchangeWhenIdle=true";

    public void testFileRoute() throws Exception {
        MockEndpoint result = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        result.expectedMinimumMessageCount(2);
        result.setResultWaitTime(5000);

        template.sendBodyAndHeader(uri, expectedBody, "cheese", 123);

        result.assertIsSatisfied();

        int fileCount = 0;
        int nullCount = 0;
        for (Exchange exchange : result.getReceivedExchanges()) {
            assertTrue(exchange instanceof FileExchange);
            FileExchange fx = (FileExchange)exchange;
            if (fx.getFile() == null) {
                nullCount++;
            } else {
                fileCount++;
            }
        }

        assertEquals(1, fileCount);
        assertTrue(nullCount > 0);

    }

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/test-inbox2");
        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(uri).to("mock:result");
            }
        };
    }
}
