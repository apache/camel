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
package org.apache.camel.builder.endpoint;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

public class PollDynamicFileNameTest extends BaseEndpointDslTest {

    private static final String TEST_DATA_DIR = BaseEndpointDslTest.generateUniquePath(PollDynamicFileNameTest.class);

    @Override
    public void doPreSetup() {
        deleteDirectory(TEST_DATA_DIR);
    }

    @Test
    public void testPollEnrichFile() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:result").message(0).body().isEqualTo("Hello World");
        getMockEndpoint("mock:result").message(1).body().isNull();

        template.sendBodyAndHeader("file://" + TEST_DATA_DIR, "Hello World", Exchange.FILE_NAME, "myfile.txt");

        template.sendBodyAndHeader("direct:start", "Foo", "target", "myfile.txt");
        template.sendBodyAndHeader("direct:start", "Bar", "target", "unknown.txt");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new EndpointRouteBuilder() {
            @Override
            public void configure() {
                from(direct("start"))
                        .poll(file(TEST_DATA_DIR).noop(true).fileName("${header.target}"), 1000)
                        .to(mock("result"));
            }
        };
    }

}
