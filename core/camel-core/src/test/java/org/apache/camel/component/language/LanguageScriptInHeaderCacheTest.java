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
package org.apache.camel.component.language;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A script from the {@link Exchange#LANGUAGE_SCRIPT} header is for that exchange only: it is compiled once, it never
 * replaces the script of the endpoint (also with cacheScript=true), and the header is removed after use.
 */
public class LanguageScriptInHeaderCacheTest extends ContextTestSupport {

    private final CountingLanguage counting = new CountingLanguage();

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        registry.bind("counting", counting);
        return registry;
    }

    @Test
    public void testResourceScriptCachedNotReplacedByHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resource");
        mock.expectedBodiesReceived("Hello A", "Bye B", "Hello C", "Hello D");

        template.sendBody("direct:resource", "A");
        template.sendBodyAndHeader("direct:resource", "B", Exchange.LANGUAGE_SCRIPT, "Bye ${body}");
        template.sendBody("direct:resource", "C");
        template.sendBody("direct:resource", "D");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInlineScriptExpressionNotReplacedByHeader() throws Exception {
        LanguageEndpoint endpoint = context.getEndpoint(inlineUri(), LanguageEndpoint.class);
        Expression before = endpoint.getExpression();

        MockEndpoint mock = getMockEndpoint("mock:inline");
        mock.expectedBodiesReceived("Hello A", "Bye B", "Hello C");

        template.sendBody("direct:inline", "A");
        template.sendBodyAndHeader("direct:inline", "B", Exchange.LANGUAGE_SCRIPT, "Bye ${body}");
        template.sendBody("direct:inline", "C");

        assertMockEndpointsSatisfied();
        assertSame(before, endpoint.getExpression());
    }

    @Test
    public void testHeaderRemovedAfterUse() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:chain");
        mock.expectedBodiesReceived("Second Bye B");

        template.sendBodyAndHeader("direct:chain", "B", Exchange.LANGUAGE_SCRIPT, "Bye ${body}");

        assertMockEndpointsSatisfied();
        assertNull(mock.getReceivedExchanges().get(0).getMessage().getHeader(Exchange.LANGUAGE_SCRIPT));
    }

    @Test
    public void testHeaderScriptCompiledOnce() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:counting");
        mock.expectedBodiesReceived("from header");

        int before = counting.created.get();
        template.sendBodyAndHeader("direct:counting", "A", Exchange.LANGUAGE_SCRIPT, "from header");

        assertMockEndpointsSatisfied();
        assertEquals(1, counting.created.get() - before);
    }

    private static String inlineUri() {
        return "language:simple:" + URLEncoder.encode("Hello ${body}", StandardCharsets.UTF_8)
               + "?allowTemplateFromHeader=true&cacheScript=true";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:resource")
                        .to("language:simple:resource:classpath:org/apache/camel/component/language/mysimplescript.txt"
                            + "?allowTemplateFromHeader=true&cacheScript=true")
                        .to("mock:resource");

                from("direct:inline").to(inlineUri()).to("mock:inline");

                from("direct:chain")
                        .to("language:simple:" + URLEncoder.encode("First ${body}", StandardCharsets.UTF_8)
                            + "?allowTemplateFromHeader=true")
                        .to("language:simple:" + URLEncoder.encode("Second ${body}", StandardCharsets.UTF_8)
                            + "?allowTemplateFromHeader=true")
                        .to("mock:chain");

                from("direct:counting").to("language:counting:endpoint?allowTemplateFromHeader=true").to("mock:counting");
            }
        };
    }

    private static final class CountingLanguage extends LanguageSupport {

        private final AtomicInteger created = new AtomicInteger();

        @Override
        public Predicate createPredicate(String expression) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Expression createExpression(String expression) {
            created.incrementAndGet();
            return ExpressionBuilder.constantExpression(expression);
        }
    }
}
