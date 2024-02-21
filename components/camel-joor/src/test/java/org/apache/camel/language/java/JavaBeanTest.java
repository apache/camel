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
package org.apache.camel.language.java;

import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.language.joor.Java;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class JavaBeanTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .transform().method(JavaBeanTest.class, "priority")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testBean() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("User tony is a high roller", "Regular user",
                "User scott is a high roller");

        template.sendBodyAndHeader("direct:start", 123, "user", "tony");
        template.sendBodyAndHeader("direct:start", 18, "user", "mickey");
        template.sendBodyAndHeader("direct:start", 44, "user", "scott");

        MockEndpoint.assertIsSatisfied(context);
    }

    public static String priority(@Java("((int) body) / 2 > 10") boolean high, @Header("user") String user) {
        if (high) {
            return "User " + user + " is a high roller";
        } else {
            return "Regular user";
        }
    }

}
