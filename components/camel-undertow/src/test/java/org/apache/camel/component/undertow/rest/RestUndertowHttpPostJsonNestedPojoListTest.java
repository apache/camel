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
package org.apache.camel.component.undertow.rest;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RestUndertowHttpPostJsonNestedPojoListTest extends BaseUndertowTest {

    @Test
    public void testPostPojoList() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:input");
        mock.expectedMessageCount(1);

        String body = "[ {\"id\": 123, \"name\": \"Donald Duck\"}, {\"id\": 456, \"name\": \"John Doe\"} ]";
        template.sendBody("undertow:http://localhost:{{port}}/users/new", body);

        MockEndpoint.assertIsSatisfied(context);

        List<?> list = mock.getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertNotNull(list);
        assertEquals(2, list.size());

        MyUserPojo user = (MyUserPojo) list.get(0);
        assertEquals(123, user.getId());
        assertEquals("Donald Duck", user.getName());
        user = (MyUserPojo) list.get(1);
        assertEquals(456, user.getId());
        assertEquals("John Doe", user.getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // configure to use undertow on localhost with the given port
                // and enable auto binding mode
                restConfiguration()
                        .component("undertow")
                        .host("localhost")
                        .port(getPort())
                        .bindingMode(RestBindingMode.auto);

                // use the rest DSL to define the rest services
                rest("/users/")
                        .post("new").type(MyUserPojo[].class)
                        .to("mock:input");
            }
        };
    }

    public static class MyUserPojo {
        private int id;
        private String name;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
