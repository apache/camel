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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.camel.NoSuchPropertyException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class JqExpressionFromPropertyTest extends JqTestSupport {
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                var jq = expression().jq().expression(".foo").source("property:Content").end();

                from("direct:start")
                        .doTry()
                        .transform(jq)
                        .to("mock:result")
                        .doCatch(NoSuchPropertyException.class)
                        .to("mock:fail");

            }
        };
    }

    @Test
    public void testExpressionFromProperty() throws Exception {
        getMockEndpoint("mock:result")
                .expectedBodiesReceived(new TextNode("bar"));
        getMockEndpoint("mock:fail")
                .expectedMessageCount(0);

        ObjectNode node = MAPPER.createObjectNode();
        node.put("foo", "bar");

        fluentTemplate.to("direct:start")
                .withProcessor(e -> e.setProperty("Content", node))
                .send();

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testExpressionFromPropertyFail() throws Exception {
        getMockEndpoint("mock:result")
                .expectedMessageCount(0);
        getMockEndpoint("mock:fail")
                .expectedMessageCount(1);

        ObjectNode node = MAPPER.createObjectNode();
        node.put("foo", "bar");

        fluentTemplate.to("direct:start")
                .withProcessor(e -> e.getMessage().setBody(node))
                .send();

        MockEndpoint.assertIsSatisfied(context);
    }
}
