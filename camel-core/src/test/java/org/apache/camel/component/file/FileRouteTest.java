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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class FileRouteTest extends ContextTestSupport {
    protected Object expectedBody = "Hello there!";
    protected String uri = "file:target/test-default-inbox";

    public void testFileRoute() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived(expectedBody);
        result.setResultWaitTime(5000);

        template.sendBodyAndHeader(uri, expectedBody, "cheese", 123);

        assertMockEndpointsSatisifed();
    }

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/test-default-inbox");
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
