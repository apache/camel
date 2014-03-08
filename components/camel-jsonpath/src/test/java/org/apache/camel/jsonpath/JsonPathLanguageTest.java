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
import java.util.List;

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
    public void testExpression() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new File("src/test/resources/books.json"));

        Language lan = context.resolveLanguage("jsonpath");
        Expression exp = lan.createExpression("$.store.book[*].author");
        List<?> authors = exp.evaluate(exchange, List.class);
        log.info("Authors {}", authors);

        assertNotNull(authors);
        assertEquals(2, authors.size());
        assertEquals("Nigel Rees", authors.get(0));
        assertEquals("Evelyn Waugh", authors.get(1));
    }

    @Test
    public void testPredicate() throws Exception {
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
}
