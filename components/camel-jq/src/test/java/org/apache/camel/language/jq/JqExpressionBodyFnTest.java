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
package org.apache.camel.language.jq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JqExpressionBodyFnTest extends JqTestSupport {
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                var jq = expression().jq(". + [{\"array\": body()}]").source("mydata").end();

                from("direct:start")
                        .setVariable("mydata", simple("${body}"))
                        .setVariable("mydata", jq)
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testExpression() throws Exception {
        getMockEndpoint("mock:result")
                .expectedMessageCount(1);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arr = mapper.createArrayNode();

        ObjectNode user1 = mapper.createObjectNode();
        user1.put("id", 1);
        user1.put("name", "John Doe");
        arr.add(user1);

        ObjectNode user2 = mapper.createObjectNode();
        user2.put("id", 2);
        user2.put("name", "Jane Jackson");
        arr.add(user2);

        template.sendBody("direct:start", arr);

        MockEndpoint.assertIsSatisfied(context);

        String res = getMockEndpoint("mock:result").getExchanges().get(0).getVariable("mydata", String.class);
        // should copy the body into the array node
        var n = mapper.reader().readTree(res);
        var a = n.get(2);
        Assertions.assertNotNull(a);
        a = a.get("array");
        // the body functions return the data as text
        Assertions.assertTrue(a.isTextual());
    }
}
