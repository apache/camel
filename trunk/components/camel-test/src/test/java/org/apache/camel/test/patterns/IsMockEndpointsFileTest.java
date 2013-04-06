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
package org.apache.camel.test.patterns;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class IsMockEndpointsFileTest extends CamelTestSupport {

    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/input");
        deleteDirectory("target/messages");
        super.setUp();
    }

    @Override
    public String isMockEndpoints() {
        // override this method and return the pattern for which endpoints to mock.
        return "file:target*";
    }

    @Test
    public void testMockFileEndpoints() throws Exception {
        // notice we have automatic mocked all endpoints and the name of the endpoints is "mock:uri"
        MockEndpoint camel = getMockEndpoint("mock:file:target/messages/camel");
        camel.expectedMessageCount(1);

        MockEndpoint other = getMockEndpoint("mock:file:target/messages/others");
        other.expectedMessageCount(1);

        template.sendBodyAndHeader("file:target/input", "Hello Camel", Exchange.FILE_NAME, "camel.txt");
        template.sendBodyAndHeader("file:target/input", "Hello World", Exchange.FILE_NAME, "world.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/input")
                    .choice()
                        .when(body(String.class).contains("Camel")).to("file:target/messages/camel")
                        .otherwise().to("file:target/messages/others");
            }
        };
    }
}
