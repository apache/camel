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
package org.apache.camel.processor.intercept;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IsMockEndpointsFileTest extends ContextTestSupport {

    @BeforeEach
    public void cleanDirs() throws Exception {
        deleteDirectory("target/input");
        deleteDirectory("target/messages");
    }

    @Override
    public String isMockEndpoints() {
        // override this method and return the pattern for which endpoints to
        // mock.
        return "file:target*";
    }

    @Test
    public void testMockFileEndpoints() throws Exception {
        // notice we have automatic mocked all endpoints and the name of the
        // endpoints is "mock:uri"
        MockEndpoint camel = getMockEndpoint("mock:file:target/messages/camel");
        camel.expectedMessageCount(1);

        MockEndpoint other = getMockEndpoint("mock:file:target/messages/others");
        other.expectedMessageCount(1);

        template.sendBodyAndHeader(fileUri("input"), "Hello Camel", Exchange.FILE_NAME, "camel.txt");
        template.sendBodyAndHeader(fileUri("input"), "Hello World", Exchange.FILE_NAME, "world.txt");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("input")).choice().when(bodyAs(String.class).contains("Camel")).to("file:target/messages/camel")
                        .otherwise().to("file:target/messages/others");
            }
        };
    }
}
