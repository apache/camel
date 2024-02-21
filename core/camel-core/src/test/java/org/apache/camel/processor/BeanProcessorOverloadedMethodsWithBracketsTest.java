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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanProcessorOverloadedMethodsWithBracketsTest extends ContextTestSupport {

    private final String strArgWithBrackets = ")(string_with_brackets()))())";

    @Test
    public void testOverloadedMethodWithBracketsParams() {
        template.sendBody("direct:start", null);
        MockEndpoint mock = getMockEndpoint("mock:result");
        String receivedExchangeBody = mock.getExchanges().get(0).getMessage().getBody(String.class);
        assertEquals(new MyOverloadedClass().myMethod(strArgWithBrackets, strArgWithBrackets), receivedExchangeBody);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .bean(MyOverloadedClass.class, "myMethod('" + strArgWithBrackets + "', '" + strArgWithBrackets + "')")
                        .to("mock:result");
            }
        };
    }

    public static class MyOverloadedClass {
        public String myMethod() {
            return "";
        }

        public String myMethod(String str) {
            return str;
        }

        public String myMethod(String str1, String str2) {
            return str1 + str2;
        }
    }
}
