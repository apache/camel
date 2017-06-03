/**
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
package org.apache.camel.jsonpath;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.Option;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.spi.Language;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JsonPathLanguageTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testExpressionArray() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new File("src/test/resources/books.json"));

        Language lan = context.resolveLanguage("jsonpath");
        Expression exp = lan.createExpression("$.store.book[*].author");
        List<?> authors = exp.evaluate(exchange, List.class);
        log.debug("Authors {}", authors);

        assertNotNull(authors);
        assertEquals(2, authors.size());
        assertEquals("Nigel Rees", authors.get(0));
        assertEquals("Evelyn Waugh", authors.get(1));

        exp = lan.createExpression("$.store.bicycle.price");
        String price = exp.evaluate(exchange, String.class);
        assertEquals("Got a wrong result", "19.95", price);
    }

    @Test
    public void testExpressionField() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new File("src/test/resources/type.json"));

        Language lan = context.resolveLanguage("jsonpath");
        Expression exp = lan.createExpression("$.kind");
        String kind = exp.evaluate(exchange, String.class);

        assertNotNull(kind);
        assertEquals("full", kind);

        exp = lan.createExpression("$.type");
        String type = exp.evaluate(exchange, String.class);
        assertNotNull(type);
        assertEquals("customer", type);
    }

    @Test
    public void testExpressionPojo() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Map pojo = new HashMap();
        pojo.put("kind", "full");
        pojo.put("type", "customer");
        exchange.getIn().setBody(pojo);

        Language lan = context.resolveLanguage("jsonpath");
        Expression exp = lan.createExpression("$.kind");
        String kind = exp.evaluate(exchange, String.class);

        assertNotNull(kind);
        assertEquals("full", kind);

        exp = lan.createExpression("$.type");
        String type = exp.evaluate(exchange, String.class);
        assertNotNull(type);
        assertEquals("customer", type);
    }

    @Test
    public void testPredicate() throws Exception {
        // Test books.json file
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new File("src/test/resources/books.json"));

        Language lan = context.resolveLanguage("jsonpath");
        Predicate pre = lan.createPredicate("$.store.book[?(@.price < 10)]");
        boolean cheap = pre.matches(exchange);
        assertTrue("Should have cheap books", cheap);

        pre = lan.createPredicate("$.store.book[?(@.price > 30)]");
        boolean expensive = pre.matches(exchange);
        assertFalse("Should not have expensive books", expensive);
    }

    @Test
    public void testSuppressException() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new File("src/test/resources/type.json"));

        JsonPathLanguage lan = (JsonPathLanguage) context.resolveLanguage("jsonpath");
        lan.setOption(Option.SUPPRESS_EXCEPTIONS);

        Expression exp = lan.createExpression("$.foo");
        String nofoo = exp.evaluate(exchange, String.class);

        assertNull(nofoo);
    }

}
