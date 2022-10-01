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

import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

public class FileDelayTest extends BaseEndpointDslTest {
    private static final String TEST_DATA_DIR = BaseEndpointDslTest.generateUniquePath(BaseEndpointDslTest.class);

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory(TEST_DATA_DIR);
        super.setUp();
        template.sendBodyAndHeader("file://" + TEST_DATA_DIR, "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://" + TEST_DATA_DIR, "Bye World", Exchange.FILE_NAME, "bye.txt");
    }

    @Test
    public void testDelay() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World");
        mock.message(1).arrives().between(1500, 3000).millis().afterPrevious();

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new EndpointRouteBuilder() {
            public void configure() throws Exception {
                from(file(TEST_DATA_DIR).delay(2).timeUnit(TimeUnit.SECONDS).delete(true).maxMessagesPerPoll(1))
                        .convertBodyTo(String.class)
                        .to(mock("result"));
            }
        };
    }

}
