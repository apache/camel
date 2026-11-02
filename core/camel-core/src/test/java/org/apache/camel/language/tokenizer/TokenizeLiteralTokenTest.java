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

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.builder.Builder;
import org.apache.camel.builder.LanguageBuilderFactory;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A token is literal text unless regex is true (CAMEL-25015)
 */
public class TokenizeLiteralTokenTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private static LanguageBuilderFactory lang() {
        return new LanguageBuilderFactory();
    }

    private List<String> split(Expression expression, String body) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").split(expression).to("mock:split");
            }
        });
        context.start();
        Exchange out = template.send("direct:start", e -> {
            e.getMessage().setBody(body);
            e.getMessage().setHeader("sep", "|");
        });
        assertThat(out.getException()).isNull();
        MockEndpoint mock = getMockEndpoint("mock:split");
        return mock.getReceivedExchanges().stream().map(e -> e.getMessage().getBody(String.class)).toList();
    }

    @Test
    public void testPipe() throws Exception {
        assertThat(split(lang().tokenize().token("|").end(), "a|b|c")).containsExactly("a", "b", "c");
    }

    @Test
    public void testDot() throws Exception {
        assertThat(split(lang().tokenize().token(".").end(), "a.b.c")).containsExactly("a", "b", "c");
    }

    @Test
    public void testDollar() throws Exception {
        assertThat(split(lang().tokenize().token("$").end(), "a$b$c")).containsExactly("a", "b", "c");
    }

    @Test
    public void testStar() throws Exception {
        assertThat(split(lang().tokenize().token("*").end(), "a*b*c")).containsExactly("a", "b", "c");
    }

    @Test
    public void testPipeFromValueBuilder() throws Exception {
        assertThat(split(Builder.body().tokenize("|"), "a|b|c")).containsExactly("a", "b", "c");
    }

    @Test
    public void testPipeGroupKeepsTokenAsDelimiter() throws Exception {
        assertThat(split(lang().tokenize().token("|").group(2).end(), "a|b|c|d")).containsExactly("a|b", "c|d");
    }

    @Test
    public void testPipeFromSimpleGroupKeepsTokenAsDelimiter() throws Exception {
        assertThat(split(lang().tokenize().token("${header.sep}").group(2).end(), "a|b|c|d")).containsExactly("a|b", "c|d");
    }

    @Test
    public void testRegexCharactersWithoutRegexAreLiteral() throws Exception {
        assertThat(split(lang().tokenize().token("\\s+").end(), "a b\\s+c")).containsExactly("a b", "c");
    }

    @Test
    public void testRegex() throws Exception {
        assertThat(split(lang().tokenize().token("\\s+").regex(true).end(), "a  b\tc")).containsExactly("a", "b", "c");
    }

    @Test
    public void testEscapedPipeWithRegex() throws Exception {
        assertThat(split(lang().tokenize().token("\\|").regex(true).end(), "a|b|c")).containsExactly("a", "b", "c");
    }

    @Test
    public void testEscapedCarriageReturnNewLine() throws Exception {
        // as the token is written in XML or YAML: backslash r backslash n (4 characters)
        assertThat(split(lang().tokenize().token("\\r\\n").end(), "a\r\nb\r\nc")).containsExactly("a", "b", "c");
    }

    @Test
    public void testEscapedTab() throws Exception {
        assertThat(split(lang().tokenize().token("\\t").end(), "a\tb\tc")).containsExactly("a", "b", "c");
    }

    @Test
    public void testPairWithParentheses() throws Exception {
        assertThat(split(lang().tokenize().token("(").endToken(")").end(), "(a)(b)")).containsExactly("a", "b");
    }

    @Test
    public void testPairWithDotInEndToken() throws Exception {
        assertThat(split(lang().tokenize().token("[rec]").endToken("END.").end(), "[rec]1 ENDS HERE END.[rec]2END."))
                .containsExactly("1 ENDS HERE ", "2");
    }

    @Test
    public void testPairWithBrackets() throws Exception {
        assertThat(split(lang().tokenize().token("[").endToken("]").end(), "[a][b]")).containsExactly("a", "b");
    }
}
