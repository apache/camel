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

package org.apache.camel.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;

public class RouteTemplateRedeliveryTest extends ContextTestSupport {

    @Test
    public void testCreateRouteFromRouteTemplate() throws Exception {
        assertEquals(1, context.getRouteTemplateDefinitions().size());

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("myCount", "3");
        parameters.put("myFac", "1");
        context.addRouteFromTemplate("first", "myTemplate", parameters);

        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("3", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate")
                        .templateParameter("myCount")
                        .templateParameter("myFac")
                        .from("direct:start")
                        .onException(Exception.class)
                        .maximumRedeliveryDelay(1)
                        .maximumRedeliveries("{{myCount}}")
                        .collisionAvoidanceFactor("{{myFac}}")
                        .onRedelivery(e -> {
                            e.getMessage().setBody(e.getMessage().getHeader(Exchange.REDELIVERY_COUNTER));
                        })
                        .handled(true)
                        .end()
                        .throwException(new IllegalArgumentException("Forced"));
            }
        };
    }
}
