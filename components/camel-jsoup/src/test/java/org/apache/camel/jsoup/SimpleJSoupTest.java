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
package org.apache.camel.jsoup;

import org.apache.camel.Expression;
import org.apache.camel.test.junit6.LanguageTestSupport;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SimpleJSoupTest extends LanguageTestSupport {

    @Override
    protected String getLanguageName() {
        return "simple";
    }

    @Test
    public void testHtmlClean() throws Exception {
        exchange.getMessage().setBody("<p><a href='https://example.com/' onclick='stealCookies()'>Link</a></p>");

        Expression expression = context.resolveLanguage("simple").createExpression("${htmlClean()}");
        String s = expression.evaluate(exchange, String.class);
        assertEquals("<p><a href=\"https://example.com/\" rel=\"nofollow\">Link</a></p>", s);

        expression = context.resolveLanguage("simple").createExpression("${htmlClean(${body})}");
        s = expression.evaluate(exchange, String.class);
        assertEquals("<p><a href=\"https://example.com/\" rel=\"nofollow\">Link</a></p>", s);
    }

    @Test
    public void testHtmlParse() throws Exception {
        exchange.getMessage()
                .setBody("<html><title>My Camel</title><body><p>Some blah blah</p></body></html>");

        Expression expression = context.resolveLanguage("simple").createExpression("${htmlParse()}");
        Document d = expression.evaluate(exchange, Document.class);
        assertNotNull(d);
        assertEquals("My Camel", d.title());
        String p = d.body().text();
        assertEquals("Some blah blah", p);

        expression = context.resolveLanguage("simple").createExpression("${htmlParse(${body})}");
        d = expression.evaluate(exchange, Document.class);
        assertNotNull(d);
        assertEquals("My Camel", d.title());
        p = d.body().text();
        assertEquals("Some blah blah", p);
    }

    @Test
    public void testHtmlDecode() throws Exception {
        exchange.getMessage()
                .setBody("<html><title>My Camel</title><body><p>Some blah blah</p></body></html>");

        Expression expression = context.resolveLanguage("simple").createExpression("${htmlDecode()}");
        String s = expression.evaluate(exchange, String.class);
        assertEquals("My Camel Some blah blah", s);

        expression = context.resolveLanguage("simple").createExpression("${htmlDecode(${body})}");
        s = expression.evaluate(exchange, String.class);
        assertEquals("My Camel Some blah blah", s);
    }

}
