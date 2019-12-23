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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class FileSplitStreamingWithChoiceTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/filesplit");
        super.setUp();
    }

    @Test
    public void testSplitStreamingWithChoice() throws Exception {
        getMockEndpoint("mock:other").expectedMessageCount(0);

        MockEndpoint mock = getMockEndpoint("mock:body");
        mock.expectedBodiesReceived("line1", "line2", "line3");

        // should be moved to this directory after we are done
        mock.expectedFileExists("target/data/filesplit/.camel/splitme.txt");

        String body = "line1" + LS + "line2" + LS + "line3";
        template.sendBodyAndHeader("file://target/data/filesplit", body, Exchange.FILE_NAME, "splitme.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/data/filesplit?initialDelay=0&delay=10").split(body().tokenize(LS)).streaming().to("mock:split").choice().when(bodyAs(String.class).isNotNull())
                    .to("mock:body").otherwise().to("mock:other").end();
            }
        };
    }
}
