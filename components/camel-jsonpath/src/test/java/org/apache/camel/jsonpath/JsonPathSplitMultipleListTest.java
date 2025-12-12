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
package org.apache.camel.jsonpath;

import java.io.File;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JsonPathSplitMultipleListTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start2")
                        // we select all books, but since we split after wards then ensure
                        // it will be wrapped inside a List object.
                        .split().jsonpath("$.store.book", List.class)
                        .to("mock:authors2")
                        .convertBodyTo(String.class);

                from("direct:start3")
                        // we select all books, but since we split after wards then ensure
                        // it will be wrapped inside a List object.
                        .split().jsonpath("$.store.book[*]", List.class)
                        .to("mock:authors3")
                        .convertBodyTo(String.class);
            }
        };
    }

    @Test
    public void testSplit() throws Exception {
        getMockEndpoint("mock:authors2").expectedMessageCount(3);
        getMockEndpoint("mock:authors3").expectedMessageCount(3);

        String out = template.requestBody("direct:start2", new File("src/test/resources/books.json"), String.class);
        assertNotNull(out);
        out = template.requestBody("direct:start3", new File("src/test/resources/books.json"), String.class);
        assertNotNull(out);

        MockEndpoint.assertIsSatisfied(context);
    }

}
