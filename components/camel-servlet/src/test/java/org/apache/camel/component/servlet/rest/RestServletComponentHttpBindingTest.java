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
package org.apache.camel.component.servlet.rest;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.servlet.ServletCamelRouterTestSupport;
import org.apache.camel.component.servlet.ServletComponent;
import org.apache.camel.component.servlet.ServletRestHttpBinding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestServletComponentHttpBindingTest extends ServletCamelRouterTestSupport {

    @Test
    void testComponentRestBindingIsUsed() throws Exception {
        WebResponse response = query(new GetMethodWebRequest(contextUrl + "/services/users/123/basic"));

        assertEquals(200, response.getResponseCode());
        assertEquals("123:true", response.getText());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getComponent("servlet", ServletComponent.class).setHttpBinding(new MarkingRestHttpBinding());

                restConfiguration().component("servlet");

                rest("/users/").get("{id}/basic").to("direct:basic");

                from("direct:basic").transform(simple("${header.id}:${header.marked}"));
            }
        };
    }

    private static final class MarkingRestHttpBinding extends ServletRestHttpBinding {
        @Override
        public void readRequest(HttpServletRequest request, Message message) {
            super.readRequest(request, message);
            message.setHeader("marked", "true");
        }
    }
}
