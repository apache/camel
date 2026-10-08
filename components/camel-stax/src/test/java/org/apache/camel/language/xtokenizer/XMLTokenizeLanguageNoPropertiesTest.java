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
package org.apache.camel.language.xtokenizer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The xtokenize language used without any options, as with the generic language expression.
 */
public class XMLTokenizeLanguageNoPropertiesTest extends CamelTestSupport {

    private static final String BODY
            = "<?xml version='1.0' encoding='UTF-8'?><parent><child id='a'>A</child><child id='b'>B</child></parent>";

    @Test
    public void testSplitWithLanguageExpression() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("<child id='a'>A</child>", "<child id='b'>B</child>");

        template.sendBody("direct:start", BODY);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testCreateExpressionWithoutProperties() throws Exception {
        Expression expression = context.resolveLanguage("xtokenize").createExpression("//child");
        expression.init(context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(BODY);

        List<String> tokens = new ArrayList<>();
        Iterator<?> it = expression.evaluate(exchange, Iterator.class);
        while (it.hasNext()) {
            tokens.add(context.getTypeConverter().convertTo(String.class, it.next()));
        }

        assertThat(tokens).containsExactly("<child id='a'>A</child>", "<child id='b'>B</child>");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").split().language("xtokenize", "//child").to("mock:result");
            }
        };
    }
}
