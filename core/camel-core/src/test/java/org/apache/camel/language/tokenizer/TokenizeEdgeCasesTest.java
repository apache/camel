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
package org.apache.camel.language.tokenizer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.Builder;
import org.apache.camel.builder.LanguageBuilderFactory;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TokenizeEdgeCasesTest extends ContextTestSupport {

    private static final String ORDERS = "<?xml version=\"1.0\"?>%s<root%s><order>1</order><order>2</order></root>";

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private static LanguageBuilderFactory lang() {
        return new LanguageBuilderFactory();
    }

    private MockEndpoint split(Expression expression, Object body, String header, Object value) throws Exception {
        return split(expression, body, e -> {
            if (header != null) {
                e.getMessage().setHeader(header, value);
            }
        });
    }

    private MockEndpoint split(Expression expression, Object body, Consumer<Exchange> setup) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").split(expression).to("mock:split");
            }
        });
        context.start();
        Exchange out = template.send("direct:start", e -> {
            e.getMessage().setBody(body);
            setup.accept(e);
        });
        assertThat(out.getException()).isNull();
        return getMockEndpoint("mock:split");
    }

    private static List<String> bodies(MockEndpoint mock) {
        return mock.getReceivedExchanges().stream().map(e -> e.getMessage().getBody(String.class)).toList();
    }

    @Test
    public void testNullBodyHasNoParts() throws Exception {
        MockEndpoint mock = split(lang().tokenize().token(",").end(), null, null, null);
        assertThat(mock.getReceivedExchanges()).isEmpty();
    }

    @Test
    public void testSkipFirstOnEmptyBody() throws Exception {
        MockEndpoint mock = split(lang().tokenize().token("\n").skipFirst(true).end(), "", null, null);
        assertThat(mock.getReceivedExchanges()).isEmpty();
    }

    @Test
    public void testSkipFirstWithoutGroupFromValueBuilder() throws Exception {
        MockEndpoint mock = split(Builder.body().tokenize(",", (String) null, true), "header,a,b", null, null);
        assertThat(bodies(mock)).containsExactly("a", "b");
    }

    @Test
    public void testPairFromSource() throws Exception {
        MockEndpoint mock = split(lang().tokenize().token("[").endToken("]").source("header:data").end(),
                "[body1][body2]", "data", "[hdr1][hdr2]");
        assertThat(bodies(mock)).containsExactly("hdr1", "hdr2");
    }

    @Test
    public void testPairSkipFirst() throws Exception {
        MockEndpoint mock = split(lang().tokenize().token("[").endToken("]").skipFirst(true).end(), "[1][2][3]", null, null);
        assertThat(bodies(mock)).containsExactly("2", "3");
    }

    @Test
    public void testPairGroupHasNoStartTokenBetweenParts() throws Exception {
        MockEndpoint mock = split(lang().tokenize().token("<a>").endToken("</a>").includeTokens(true).group(2).end(),
                "<a>1</a><a>2</a><a>3</a>", null, null);
        assertThat(bodies(mock)).containsExactly("<a>1</a><a>2</a>", "<a>3</a>");
    }

    @Test
    public void testPairGroupWithoutTokensKeepsStartTokenAsBoundary() throws Exception {
        MockEndpoint mock = split(lang().tokenize().token("[").endToken("]").group(2).end(),
                "[1][2][3]", null, null);
        assertThat(bodies(mock)).containsExactly("1[2", "3");
    }

    @Test
    public void testPairWithSameStartAndEndTokenIsRejected() {
        assertThatThrownBy(() -> split(lang().tokenize().token("'").endToken("'").end(), "'a' 'b'", null, null))
                .isInstanceOf(FailedToCreateRouteException.class)
                .rootCause().hasMessageContaining("The start and end token must be different");
    }

    @Test
    public void testXmlSkipFirst() throws Exception {
        MockEndpoint mock = split(lang().tokenize().token("order").xml(true).skipFirst(true).end(),
                String.format(ORDERS, "", ""), null, null);
        assertThat(bodies(mock)).containsExactly("<order>2</order>");
    }

    @Test
    public void testXmlGroupSkipFirst() throws Exception {
        MockEndpoint mock = split(lang().tokenize().token("order").xml(true).group(2).skipFirst(true).end(),
                "<root><order>1</order><order>2</order><order>3</order></root>", null, null);
        assertThat(bodies(mock)).containsExactly("<order>2</order><order>3</order>");
    }

    @Test
    public void testWrapWithCommentBeforeRoot() throws Exception {
        MockEndpoint mock = split(lang().tokenize().token("order").xml(true).inheritNamespaceTagName("*").end(),
                String.format(ORDERS, "<!-- licensed to you -->", ""), null, null);
        assertThat(bodies(mock)).containsExactly(
                "<?xml version=\"1.0\"?><!-- licensed to you --><root><order>1</order></root>",
                "<?xml version=\"1.0\"?><!-- licensed to you --><root><order>2</order></root>");
    }

    @Test
    public void testWrapWithDoctype() throws Exception {
        String doctype = "<!DOCTYPE root [<!ELEMENT root (order*)>]>";
        MockEndpoint mock = split(lang().tokenize().token("order").xml(true).inheritNamespaceTagName("*").end(),
                String.format(ORDERS, doctype, ""), null, null);
        assertThat(bodies(mock)).containsExactly(
                "<?xml version=\"1.0\"?>" + doctype + "<root><order>1</order></root>",
                "<?xml version=\"1.0\"?>" + doctype + "<root><order>2</order></root>");
    }

    @Test
    public void testWrapWithDoctypeWhitespaceBeforeClose() throws Exception {
        String doctype = "<!DOCTYPE root [<!ELEMENT root (order*)>] \n>";
        MockEndpoint mock = split(lang().tokenize().token("order").xml(true).inheritNamespaceTagName("*").end(),
                String.format(ORDERS, doctype, ""), null, null);
        assertThat(bodies(mock)).containsExactly(
                "<?xml version=\"1.0\"?>" + doctype + "<root><order>1</order></root>",
                "<?xml version=\"1.0\"?>" + doctype + "<root><order>2</order></root>");
    }

    @Test
    public void testWrapWithMultiByteCharactersBeforeToken() throws Exception {
        byte[] body = String.format(ORDERS, "", " name=\"æøå\"").getBytes(StandardCharsets.UTF_8);
        MockEndpoint mock = split(lang().tokenize().token("order").xml(true).inheritNamespaceTagName("*").end(),
                body, e -> e.setProperty(Exchange.CHARSET_NAME, "UTF-8"));
        assertThat(bodies(mock)).containsExactly(
                "<?xml version=\"1.0\"?><root name=\"æøå\"><order>1</order></root>",
                "<?xml version=\"1.0\"?><root name=\"æøå\"><order>2</order></root>");
    }
}
