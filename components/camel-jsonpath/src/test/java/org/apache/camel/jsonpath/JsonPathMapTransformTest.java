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
package org.apache.camel.jsonpath;

import java.io.FileInputStream;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.Configuration;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JsonPathMapTransformTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .transform().jsonpath("$.store.book[*].author")
                    .to("mock:authors");
            }
        };
    }

    @Test
    public void testAuthors() throws Exception {
        getMockEndpoint("mock:authors").expectedMessageCount(1);

        // should be a map
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(new FileInputStream("src/test/resources/books.json"), "utf-8");
        assertIsInstanceOf(Map.class, document);

        template.sendBody("direct:start", document);

        assertMockEndpointsSatisfied();

        List<?> authors = getMockEndpoint("mock:authors").getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertEquals("Nigel Rees", authors.get(0));
        assertEquals("Evelyn Waugh", authors.get(1));
    }

}
