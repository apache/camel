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
package org.apache.camel.language.simple;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

public class FileSimpleJsonPathTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testFileConvertBodyToSimpleJsonPath() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:src/test/resources?fileName=books.json&noop=true&initialDelay=0")
                        .convertBodyTo(JsonObject.class)
                        .setHeader("title1", simple("${simpleJsonpath(library.book[0].title)}"))
                        .setHeader("title2", simple("${simpleJsonpath(library.book[1].title)}"))
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).body().isInstanceOf(JsonObject.class);

        getMockEndpoint("mock:result").expectedHeaderReceived("title1", "No Title");
        getMockEndpoint("mock:result").expectedHeaderReceived("title2", "1984");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFileSimpleJsonPath() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:src/test/resources?fileName=books.json&noop=true&initialDelay=0")
                        .setHeader("title1", simple("${simpleJsonpath(library.book[0].title)}"))
                        .setHeader("title2", simple("${simpleJsonpath(library.book[1].title)}"))
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).body().isInstanceOf(GenericFile.class);

        getMockEndpoint("mock:result").expectedHeaderReceived("title1", "No Title");
        getMockEndpoint("mock:result").expectedHeaderReceived("title2", "1984");

        assertMockEndpointsSatisfied();
    }

}
