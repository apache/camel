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
package org.apache.camel.component.servlet;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class ServletChoiceBeanTest extends ServletCamelRouterTestSupport {

    @Test
    public void testClient() throws Exception {
        getMockEndpoint("mock:bean").expectedMessageCount(1);

        WebRequest req = new GetMethodWebRequest(contextUrl + "/services/hello");
        req.setParameter("id", "123");
        WebResponse response = query(req);

        assertEquals(200, response.getResponseCode());
        assertEquals("Client is Donald Duck", response.getText(), "The response message is wrong");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testNoClient() throws Exception {
        getMockEndpoint("mock:bean").expectedMessageCount(1);

        WebRequest req = new GetMethodWebRequest(contextUrl + "/services/hello");
        try {
            query(req);
            fail("Should throw exception");
        } catch (HttpNotFoundException e) {
            assertEquals(404, e.getResponseCode());
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("servlet:/hello")
                        .bean(ServletChoiceBeanTest.class, "findClient(${header.id})")
                        .to("mock:bean")
                        .choice()
                        .when(simple("${body} == null"))
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                        .otherwise()
                        .setBody(simple("Client is ${body}"))
                        .end();
            }
        };
    }

    public static String findClient(String id) throws Exception {
        if ("123".equals(id)) {
            return "Donald Duck";
        }
        return null;
    }

}
