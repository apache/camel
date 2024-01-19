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

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.stream.FileInputStreamCache;
import org.junit.jupiter.api.Test;

public class StreamCachingChoiceWithVariableTest extends ContextTestSupport {

    private static final String TEST_FILE = "src/test/resources/org/apache/camel/converter/stream/test.xml";

    @Test
    public void testStreamCaching() throws Exception {
        getMockEndpoint("mock:paris").expectedMessageCount(0);
        getMockEndpoint("mock:madrid").expectedMessageCount(0);
        getMockEndpoint("mock:london").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(1);

        File file = new File(TEST_FILE);
        FileInputStreamCache cache = new FileInputStreamCache(file);
        template.sendBody("direct:start", cache);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .setVariable("foo").simple("${body}")
                        .choice()
                            .when().simple("${variable:foo} contains 'Paris'")
                                .to("mock:paris")
                            .when().simple("${variable:foo} contains 'London'")
                                .to("mock:london")
                            .otherwise()
                                .to("mock:other")
                        .end()
                        .choice()
                            .when().simple("${variable:foo} contains 'Paris'")
                                .to("mock:paris")
                            .when().simple("${variable:foo} contains 'Madrid'")
                                .to("mock:madrid")
                            .otherwise()
                                .to("mock:other")
                        .end();
            }
        };
    }
}
