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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JsonPathExchangePropertyTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setProperty("foo").jsonpath("$.store.bicycle.color", String.class)
                        .log("${exchangeProperty.foo}")
                        .to("mock:color");
            }
        };
    }

    @Test
    public void testExchangeProperty() throws Exception {
        getMockEndpoint("mock:color").expectedMessageCount(1);

        template.sendBody("direct:start", new File("src/test/resources/books.json"));

        MockEndpoint.assertIsSatisfied(context);

        Object foo = getMockEndpoint("mock:color").getReceivedExchanges().get(0).getProperty("foo");
        Assertions.assertTrue(foo instanceof String);
        Assertions.assertEquals("red", foo);
    }

}
