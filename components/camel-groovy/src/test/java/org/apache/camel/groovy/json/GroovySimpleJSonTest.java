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
package org.apache.camel.groovy.json;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.groovy.json.internal.LazyMap;
import org.junit.jupiter.api.Test;

public class GroovySimpleJSonTest extends CamelTestSupport {

    private static final String BOOKS
            = """
                    {
                        "library": {
                            "book": [
                                {
                                    "title": "No Title",
                                    "author": "F. Scott Fitzgerald",
                                    "year": "1925",
                                    "genre": "Classic",
                                    "id": "bk101"
                                },
                                {
                                    "title": "1984",
                                    "author": "George Orwell",
                                    "year": "1949",
                                    "genre": "Dystopian",
                                    "id": "bk102"
                                }
                            ]
                        }
                    }
                    """;

    @Test
    public void testGroovySimpleJsonPath() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).body().isInstanceOf(LazyMap.class); // groovy json object
        getMockEndpoint("mock:result").expectedHeaderReceived("title1", "No Title");
        getMockEndpoint("mock:result").expectedHeaderReceived("title2", "1984");

        template.sendBody("direct:start", BOOKS);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .unmarshal().groovyJson()
                        .setHeader("title1", simple("${simpleJsonpath(library.book[0].title)}"))
                        .setHeader("title2", simple("${simpleJsonpath(library.book[1].title)}"))
                        .to("mock:result");
            }
        };
    }
}
